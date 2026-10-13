# Turns one run.sh results folder into a markdown table: per user count, the response times, the throughput,
# the errors, and the CPU and memory of each container. CPU is given as the average from 15 s in (all users
# have arrived) and the peak, which is usually the first seconds, when every virtual user starts at once.
import csv
import glob
import json
import os
import re
import sys

out = sys.argv[1]


def metric(data, name, key):
    m = data["metrics"].get(name, {})
    return m.get(key, m.get("values", {}).get(key))


def usage(path):
    cpu, mem, steady = {}, {}, {}
    if not os.path.exists(path):
        return cpu, mem, steady
    start = None
    with open(path) as f:
        for row in csv.reader(f):
            if len(row) < 4:
                continue
            name = re.sub(r"^ils-load-|-1$", "", row[1])
            try:
                t = int(row[0])
                c = float(row[2].rstrip("%"))
                m = float(row[3].rstrip("%"))
            except ValueError:
                continue
            start = t if start is None else start
            cpu[name] = max(cpu.get(name, 0), c)
            mem[name] = max(mem.get(name, 0), m)
            if t - start >= 15:
                steady.setdefault(name, []).append(c)
    return cpu, mem, {k: sum(v) / len(v) for k, v in steady.items()}


rows = []
for path in glob.glob(os.path.join(out, "k6-*.json")):
    vus = int(re.search(r"k6-(\d+)\.json", path).group(1))
    with open(path) as f:
        data = json.load(f)
    cpu, mem, steady = usage(os.path.join(out, f"stats-{vus}.csv"))
    rows.append((vus, data, cpu, mem, steady))
rows.sort(key=lambda r: r[0])

print("| Users | Requests/s | Journeys/s | Median | p90 | p95 | p99 | Failed requests | Journeys OK | Backend CPU of its 2 cores: average, peak | Backend memory of 1 GB | Postgres CPU, in cores: average, peak |")
print("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
for vus, d, cpu, mem, steady in rows:
    ms = lambda k: f"{metric(d, 'http_req_duration', k):.0f} ms"
    print(f"| {vus} | {metric(d, 'http_reqs', 'rate'):.1f} | {metric(d, 'transactions', 'rate'):.1f} "
          f"| {ms('med')} | {ms('p(90)')} | {ms('p(95)')} | {ms('p(99)')} "
          f"| {100 * metric(d, 'http_req_failed', 'value'):.2f}% | {100 * metric(d, 'journey_ok', 'value'):.1f}% "
          f"| {steady.get('backend', 0) / 2:.0f}%, {min(100, cpu.get('backend', 0) / 2):.0f}% | {mem.get('backend', 0):.0f}% "
          f"| {steady.get('postgres', 0) / 100:.1f}, {cpu.get('postgres', 0) / 100:.1f} |")
