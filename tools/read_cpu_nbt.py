import gzip
import json
import struct
import sys
import zlib
from pathlib import Path


class NbtReader:
    def __init__(self, data):
        self.data = memoryview(data)
        self.pos = 0

    def unpack(self, fmt):
        size = struct.calcsize(fmt)
        value = struct.unpack_from(fmt, self.data, self.pos)
        self.pos += size
        return value[0] if len(value) == 1 else value

    def string(self):
        size = self.unpack(">H")
        value = bytes(self.data[self.pos:self.pos + size]).decode("utf-8")
        self.pos += size
        return value

    def payload(self, tag):
        if tag == 0:
            return None
        if tag == 1:
            return self.unpack(">b")
        if tag == 2:
            return self.unpack(">h")
        if tag == 3:
            return self.unpack(">i")
        if tag == 4:
            return self.unpack(">q")
        if tag == 5:
            return self.unpack(">f")
        if tag == 6:
            return self.unpack(">d")
        if tag == 7:
            size = self.unpack(">i")
            value = list(bytes(self.data[self.pos:self.pos + size]))
            self.pos += size
            return value
        if tag == 8:
            return self.string()
        if tag == 9:
            item_tag = self.unpack(">B")
            size = self.unpack(">i")
            return [self.payload(item_tag) for _ in range(size)]
        if tag == 10:
            value = {}
            while True:
                item_tag = self.unpack(">B")
                if item_tag == 0:
                    return value
                name = self.string()
                value[name] = self.payload(item_tag)
        if tag == 11:
            size = self.unpack(">i")
            return [self.unpack(">i") for _ in range(size)]
        if tag == 12:
            size = self.unpack(">i")
            return [self.unpack(">q") for _ in range(size)]
        raise ValueError(f"Unknown NBT tag {tag} at {self.pos}")

    def root(self):
        tag = self.unpack(">B")
        name = self.string()
        return name, self.payload(tag)


def read_chunk(region_path, chunk_x, chunk_z):
    local_x = chunk_x % 32
    local_z = chunk_z % 32
    with region_path.open("rb") as handle:
        handle.seek((local_x + local_z * 32) * 4)
        location = handle.read(4)
        sector = int.from_bytes(location[:3], "big")
        if sector == 0:
            raise RuntimeError("Chunk is not present in region")
        handle.seek(sector * 4096)
        length = int.from_bytes(handle.read(4), "big")
        compression = handle.read(1)[0]
        payload = handle.read(length - 1)
    if compression == 1:
        payload = gzip.decompress(payload)
    elif compression == 2:
        payload = zlib.decompress(payload)
    elif compression != 3:
        raise RuntimeError(f"Unsupported compression {compression}")
    return NbtReader(payload).root()[1]


def main():
    region_path = Path(sys.argv[1])
    x, y, z = map(int, sys.argv[2:5])
    root = read_chunk(region_path, x // 16, z // 16)
    entities = root.get("block_entities", root.get("Level", {}).get("block_entities", []))
    matches = [e for e in entities if (e.get("x"), e.get("y"), e.get("z")) == (x, y, z)]
    if not matches:
        print(f"No block entity at {x}, {y}, {z}; found {len(entities)} in chunk")
        for entity in entities:
            print(entity.get("id"), entity.get("x"), entity.get("y"), entity.get("z"))
        return 1
    entity = matches[0]
    if len(sys.argv) > 5 and sys.argv[5] == "--summary":
        job = entity.get("job", {})
        print("job keys:", sorted(job))
        print("finalOutput:", job.get("finalOutput"))
        print("remainingAmount:", job.get("remainingAmount"))
        print("waitingFor:", json.dumps(job.get("waitingFor"), sort_keys=True))
        print("inventory entries:", len(entity.get("inventory", [])))
        for entry in entity.get("inventory", []):
            if any(term in entry.get("id", "") for term in (
                    "diaminobenzidine", "dichlorobenzidine", "ammonia",
                    "hydrochloric_acid", "diphenyl_isophthalate",
                    "polybenzimidazole", "phenol", "nitrobenzene", "hydrogen",
                    "netherite_hexammine", "netherite_trisulfate")):
                print("inventory:", json.dumps(entry, sort_keys=True))
        tasks = job.get("tasks", [])
        print("task entries:", len(tasks))
        for index, task in enumerate(tasks):
            progress = task.get("#craftingProgress", task.get("craftingProgress"))
            tag = task.get("tag", {})
            inputs = [v for v in tag.get("in", []) if v]
            outputs = [v for v in tag.get("out", []) if v]
            ids = " ".join(str(v.get("id", "")) for v in inputs + outputs)
            if progress and any(term in ids for term in (
                    "diaminobenzidine", "dichlorobenzidine", "ammonia",
                    "hydrochloric_acid", "polybenzimidazole", "phenol")):
                print(json.dumps({"index": index, "progress": progress,
                                  "inputs": inputs, "outputs": outputs}, sort_keys=True))
        return 0
    print(json.dumps(entity, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
