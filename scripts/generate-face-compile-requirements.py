"""Read the actual pinned face graphs and trim ESP-DL through its public CMake API.

No managed sources or neural network files are modified. Conv kernels for the actual graph quantization remain available for ESP32-S3/C;
unrelated operators and unused numeric formats are cut.
An unexpected model format fails configuration instead of silently removing ops.
"""
import argparse
import hashlib
from pathlib import Path
import re
import struct


class GraphReader:
    def __init__(self, path):
        self.data = path.read_bytes()
        if len(self.data) < 32 or len(self.data) > 2_000_000 or self.data[:4] != b"EDL2":
            raise ValueError(f"Unsupported face model container: {path.name}")
        self.root = 16 + self.u32(16)

    def read(self, format, at):
        size = struct.calcsize(format)
        if at < 16 or at + size > len(self.data):
            raise ValueError("Face graph offset exceeds container")
        return struct.unpack_from(format, self.data, at)[0]

    def u32(self, at):
        return self.read("<I", at)

    def field(self, table, field_id):
        vtable = table - self.read("<i", table)
        length = self.read("<H", vtable)
        entry = vtable + 4 + field_id * 2
        if entry + 2 > vtable + length:
            raise ValueError("Required face graph field missing")
        relative = self.read("<H", entry)
        if relative == 0:
            raise ValueError("Required face graph field absent")
        return table + relative

    def target(self, at):
        return at + self.u32(at)

    def operators(self):
        # espdl.fbs: Model.graph=7, Graph.node=0, Node.op_type=3.
        graph = self.target(self.field(self.root, 7))
        nodes = self.target(self.field(graph, 0))
        count = self.u32(nodes)
        if not 1 <= count <= 1000:
            raise ValueError("Unexpected face node count")
        result = set()
        quantizations = set()
        for index in range(count):
            node = self.target(nodes + 4 + index * 4)
            string = self.target(self.field(node, 3))
            length = self.u32(string)
            if not 1 <= length <= 64 or string + 4 + length >= len(self.data):
                raise ValueError("Invalid face operator string")
            operator = self.data[string + 4:string + 4 + length].decode("ascii")
            if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*", operator):
                raise ValueError("Invalid face operator name")
            result.add(operator)
            if operator == "Conv":
                # Node.attribute=5, Attribute.name=0, Attribute.s=6.
                attributes = self.target(self.field(node, 5))
                attribute_count = self.u32(attributes)
                if not 1 <= attribute_count <= 64:
                    raise ValueError("Unexpected Conv attribute count")
                quantization = None
                for attribute_index in range(attribute_count):
                    attribute = self.target(attributes + 4 + attribute_index * 4)
                    name = self.target(self.field(attribute, 0))
                    name_length = self.u32(name)
                    if not 1 <= name_length <= 64:
                        raise ValueError("Invalid Conv attribute name")
                    name_bytes = bytes(self.read("<B", name + 4 + i) for i in range(name_length))
                    if name_bytes == b"quant_type":
                        if quantization is not None:
                            raise ValueError("Duplicate Conv quantization")
                        value = self.target(self.field(attribute, 6))
                        value_length = self.u32(value)
                        if not 1 <= value_length <= 16:
                            raise ValueError("Invalid Conv quantization length")
                        quantization = bytes(self.read("<B", value + 4 + i)
                                             for i in range(value_length)).decode("ascii")
                if quantization not in ("S8", "S16", "W8A16"):
                    raise ValueError("Conv has missing/unsupported quantization")
                quantizations.add(quantization)
        return result, count, quantizations


def generate(models, kernels_path, output):
    all_operators = set()
    all_quantizations = set()
    comments = ["# Generated from pinned EDL2 graphs; do not edit managed components."]
    for path in models:
        reader = GraphReader(path)
        operators, count, quantizations = reader.operators()
        all_operators.update(operators)
        all_quantizations.update(quantizations)
        comments.append(f"# {path.name}: {count} nodes, sha256={hashlib.sha256(reader.data).hexdigest()}")
    specification = kernels_path.read_text(encoding="utf-8")
    # ESP-DL 3.3.13's runtime has two explicit helper dependencies outside the
    # exported graph: PRelu::deserialize may construct LUT, and TensorBase::assign
    # calls RequantizeLinear's 8/16-bit functions. Retain this dependency closure.
    helpers = {"RequantizeLinear"}
    if "PRelu" in all_operators:
        helpers.add("LUT")
    comments.append("# Runtime helper closure (not graph nodes): " + ", ".join(sorted(helpers)))
    all_operators.update(helpers)
    abi = re.search(r"^kernel_abi: (\d+)\s*$", specification, re.M)
    dtypes = {"S8": "s8", "S16": "s16", "W8A16": "w8a16"}
    candidates = re.findall(r"^\s+- (dl_(?:c|tie728)_([A-Za-z0-9]+)_[A-Za-z0-9_]+)\s*$", specification, re.M)
    needed_dtypes = {dtypes[quantization] for quantization in all_quantizations}
    kernels = sorted({kernel for kernel, dtype in candidates if dtype in needed_dtypes})
    comments.append("# Verified Conv quantization: " + ", ".join(sorted(all_quantizations)))
    if abi is None or not kernels or not all_operators:
        raise ValueError("ESP-DL kernel specification missing")
    text = "\n".join(comments) + f"\nkernel_abi: {abi.group(1)}\nops:\n"
    text += "".join(f"  - {op}\n" for op in sorted(all_operators)) + "kernels:\n"
    text += "".join(f"  - {kernel}\n" for kernel in kernels)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(text, encoding="utf-8")
    print(f"Face requirements: ops={','.join(sorted(all_operators))}; kernels={len(kernels)} (C/ESP32-S3; quantizations={','.join(sorted(all_quantizations))})")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, action="append", required=True)
    parser.add_argument("--kernels", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    generate(args.model, args.kernels, args.output)
