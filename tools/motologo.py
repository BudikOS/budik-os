#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Read and rebuild the Motorola boot logo partition (logo.bin).

  motologo.py list  logo.bin
  motologo.py dump  logo.bin outdir                        one PNG per image
  motologo.py build logo.bin out.bin name=image.png [...]  replace images, keep the rest

Container: 'MotoLogo\\0', u32 LE header length, then 32-byte entries (24-byte name,
u32 LE offset, u32 LE size); images start 512-byte aligned.
Image: 'MotoRun\\0', width and height as u16 BE, then a per-row RLE of BGR pixels:
u16 BE n; with 0x8000 set one pixel repeats (n & 0x7fff) times, otherwise n
literal pixels follow. Runs never cross rows."""
import os
import struct
import sys

from PIL import Image

LOGO_MAGIC = b'MotoLogo\0'
RUN_MAGIC = b'MotoRun\0'
ENTRY_SIZE = 32
ALIGN = 512
MAX_RUN = 0x7fff
PARTITION_SIZE = 16 * 1024 * 1024


def parse(data):
    if data[:9] != LOGO_MAGIC:
        raise ValueError('not a MotoLogo image')
    header_len = struct.unpack('<I', data[9:13])[0]
    entries = []
    for i in range(13, header_len, ENTRY_SIZE):
        name = data[i:i + 24].split(b'\0')[0].decode()
        offset, size = struct.unpack('<II', data[i + 24:i + 32])
        entries.append((name, data[offset:offset + size]))
    return entries


def decode(blob):
    """Return the image and the number of bytes its RLE stream used."""
    if blob[:8] != RUN_MAGIC:
        raise ValueError('not a MotoRun image')
    width, height = struct.unpack('>HH', blob[8:12])
    pos, pixels = 12, bytearray()
    while len(pixels) < width * height * 3:
        n = struct.unpack('>H', blob[pos:pos + 2])[0]
        pos += 2
        if n & 0x8000:
            pixels += blob[pos:pos + 3] * (n & MAX_RUN)
            pos += 3
        else:
            pixels += blob[pos:pos + 3 * n]
            pos += 3 * n
    return Image.frombytes('RGB', (width, height), bytes(pixels), 'raw', 'BGR'), pos


def encode_row(row, out):
    literal = []

    def flush():
        while literal:
            chunk = literal[:MAX_RUN]
            del literal[:len(chunk)]
            out.extend(struct.pack('>H', len(chunk)))
            for pixel in chunk:
                out.extend(pixel)

    x = 0
    while x < len(row):
        run = 1
        while x + run < len(row) and row[x + run] == row[x] and run < MAX_RUN:
            run += 1
        if run >= 3:
            flush()
            out.extend(struct.pack('>H', 0x8000 | run) + row[x])
        else:
            literal.extend(row[x:x + run])
        x += run
    flush()


def encode(image):
    image = image.convert('RGB')
    width, height = image.size
    raw = image.tobytes('raw', 'BGR')
    out = bytearray(RUN_MAGIC + struct.pack('>HH', width, height))
    for y in range(height):
        start = y * width * 3
        encode_row([raw[start + x * 3:start + x * 3 + 3] for x in range(width)], out)
    return bytes(out)


def build(entries):
    header_len = 13 + ENTRY_SIZE * len(entries)
    data_start = -(-header_len // ALIGN) * ALIGN
    header = bytearray(LOGO_MAGIC + struct.pack('<I', header_len))
    body = bytearray()
    for name, blob in entries:
        header += name.encode().ljust(24, b'\0') + struct.pack('<II', data_start + len(body), len(blob))
        body += blob + b'\0' * (-len(blob) % ALIGN)
    data = bytes(header.ljust(data_start, b'\0') + body)
    if len(data) > PARTITION_SIZE:
        raise ValueError(f'image too big: {len(data)} > {PARTITION_SIZE}')
    return data


def main():
    if len(sys.argv) < 3 or sys.argv[1] not in ('list', 'dump', 'build'):
        sys.exit(__doc__)
    cmd, path = sys.argv[1:3]
    with open(path, 'rb') as f:
        entries = parse(f.read())
    if cmd == 'list':
        for name, blob in entries:
            image, used = decode(blob)
            print(name, image.size, len(blob), 'OK' if used == len(blob) else f'used {used}')
    elif cmd == 'dump':
        os.makedirs(sys.argv[3], exist_ok=True)
        for name, blob in entries:
            decode(blob)[0].save(os.path.join(sys.argv[3], name + '.png'))
    else:
        replace = dict(arg.split('=', 1) for arg in sys.argv[4:])
        unknown = set(replace) - {name for name, _ in entries}
        if unknown:
            sys.exit(f'unknown names: {sorted(unknown)}')
        entries = [(name, encode(Image.open(replace[name])) if name in replace else blob)
                   for name, blob in entries]
        data = build(entries)
        with open(sys.argv[3], 'wb') as f:
            f.write(data)
        print('wrote', len(data))


if __name__ == '__main__':
    main()
