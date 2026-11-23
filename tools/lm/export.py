# SPDX-License-Identifier: GPL-3.0-only
"""
Builds the on-device language model release from SmolLM2-135M (Apache-2.0).

  - exports the model to ONNX without a key/value cache: inputs input_ids and attention_mask,
    output logits, so the phone runs one plain pass over the text before the cursor;
  - quantises its weights to 8 bits;
  - writes the tokenizer as the plain-text file the app reads (see LmTokenizer.parse);
  - checks the quantised model against the original and writes samples for the tokenizer check.

Usage: python export.py <out dir>
"""
import base64, hashlib, json, os, sys

import numpy as np
import onnxruntime as ort
import torch
from huggingface_hub import HfApi, hf_hub_download
from onnxruntime.quantization import QuantType, quantize_dynamic
from transformers import AutoModelForCausalLM, AutoTokenizer

REPO = "HuggingFaceTB/SmolLM2-135M"
out = sys.argv[1]
os.makedirs(out, exist_ok=True)
revision = HfApi().model_info(REPO).sha
print("revision", revision)

tok = AutoTokenizer.from_pretrained(REPO, revision=revision)
model = AutoModelForCausalLM.from_pretrained(REPO, revision=revision, torch_dtype=torch.float32,
                                             attn_implementation="eager")
model.config.use_cache = False
model.eval()


class Logits(torch.nn.Module):
    def __init__(self, m):
        super().__init__()
        self.m = m

    def forward(self, input_ids, attention_mask):
        return self.m(input_ids=input_ids, attention_mask=attention_mask, use_cache=False).logits


sample = tok("The weather today is", return_tensors="pt")
fp32 = os.path.join(out, "fp32.onnx")
with torch.no_grad():
    torch.onnx.export(
        Logits(model), (sample.input_ids, sample.attention_mask), fp32,
        input_names=["input_ids", "attention_mask"], output_names=["logits"],
        dynamic_axes={"input_ids": {0: "batch", 1: "seq"}, "attention_mask": {0: "batch", 1: "seq"},
                      "logits": {0: "batch", 1: "seq"}},
        opset_version=17, do_constant_folding=True, dynamo=False)

int8 = os.path.join(out, "smollm2-135m-int8.onnx")
quantize_dynamic(fp32, int8, weight_type=QuantType.QInt8)
os.remove(fp32)
for f in os.listdir(out):
    if f.endswith(".data") or f.endswith(".onnx.data"):
        os.remove(os.path.join(out, f))

# Tokenizer, as plain text.
tj = json.load(open(hf_hub_download(REPO, "tokenizer.json", revision=revision), encoding="utf-8"))
bpe = tj["model"]
assert bpe["type"] == "BPE", bpe["type"]
vocab = dict(bpe["vocab"])
for added in tj.get("added_tokens", []):
    vocab.setdefault(added["content"], added["id"])
merges = [m.split(" ", 1) if isinstance(m, str) else m for m in bpe["merges"]]
pre = json.dumps(tj.get("pre_tokenizer"))
split_digits = '"Digits"' in pre and '"individual_digits": true' in pre
ignore_merges = bool(bpe.get("ignore_merges", False))
print("pre_tokenizer", pre)
print("ignore_merges", ignore_merges, "split_digits", split_digits)
for t in vocab:
    assert not any(c in t for c in "\t\n"), repr(t)
for a, b in merges:
    assert " " not in a and " " not in b and "\t" not in a + b and "\n" not in a + b, (a, b)
with open(os.path.join(out, "smollm2-135m-tokenizer.txt"), "w", encoding="utf-8", newline="\n") as f:
    f.write(f"ignore_merges={int(ignore_merges)} split_digits={int(split_digits)}\n")
    f.write(f"V\t{len(vocab)}\n")
    for t, i in vocab.items():
        f.write(f"{t}\t{i}\n")
    f.write(f"M\t{len(merges)}\n")
    for a, b in merges:
        f.write(f"{a} {b}\n")

# Samples for the tokenizer check: the app's encoder must give exactly these ids.
samples = [
    "Hello, how are you doing today?",
    "I'll see you at 10:30 tomorrow, don't be late!",
    "The quick brown fox jumps over the lazy dog.",
    "We've got 2024 reasons     to   celebrate\nand more.",
    "Thanks so much for your help with the project",
    "café naïve résumé — “quoted” text…",
    "email me at someone@example.com or call +212 600 123 456",
    "  leading spaces and trailing ",
    "It's a well-known fact that it's raining",
    "Can you send me the file before Monday?",
]
with open(os.path.join(out, "samples.txt"), "w", encoding="utf-8") as f:
    for s in samples:
        ids = tok.encode(s, add_special_tokens=False)
        f.write(base64.b64encode(s.encode("utf-8")).decode() + "\t" + ",".join(map(str, ids)) + "\n")

# The quantised model must still agree with the original about what comes next.
eot = vocab["<|endoftext|>"]
sess = ort.InferenceSession(int8, providers=["CPUExecutionProvider"])
names = [i.name for i in sess.get_inputs()]
report = []
agree = 0
prompts = ["I would like to thank you for your", "See you", "Can you send me the", "The meeting is on",
           "Happy birthday to", "What time do you want to"]
for p in prompts:
    ids = [eot] + tok.encode(p, add_special_tokens=False)
    with torch.no_grad():
        ref = model(input_ids=torch.tensor([ids])).logits[0, -1].numpy()
    feed = {"input_ids": np.array([ids], dtype=np.int64), "attention_mask": np.ones((1, len(ids)), dtype=np.int64)}
    got = sess.run(None, {k: v for k, v in feed.items() if k in names})[0][0, -1]
    agree += int(np.argmax(ref) == np.argmax(got))
    top = np.argsort(-got)[:5]
    report.append(f"{p!r} -> " + ", ".join(repr(tok.decode([int(t)])) for t in top))
print("\n".join(report))
print(f"argmax agreement {agree}/{len(prompts)}")


def sha(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


with open(os.path.join(out, "notes.md"), "w", encoding="utf-8") as f:
    f.write("On-device language model for SonderKey's predictions. Downloaded by the app on request; "
            "runs only on the phone.\n\n")
    f.write(f"Source: [{REPO}](https://huggingface.co/{REPO}) at `{revision}`, Apache-2.0. "
            "Exported to ONNX without a key/value cache and quantised to 8 bits.\n\n")
    f.write("| File | Size | SHA-256 |\n|---|---|---|\n")
    for name in ["smollm2-135m-int8.onnx", "smollm2-135m-tokenizer.txt"]:
        path = os.path.join(out, name)
        f.write(f"| `{name}` | {os.path.getsize(path)} | `{sha(path)}` |\n")
    f.write(f"\nQuantised vs original, same next token: {agree}/{len(prompts)}\n\n```\n" + "\n".join(report) + "\n```\n")
