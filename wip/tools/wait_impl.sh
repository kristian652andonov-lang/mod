#!/bin/bash
# waits until the workflow journal $1 has a result (or failure) for the agent labelled $2, then prints it and exits
J=$1; L=$2
while true; do
  r=$(python3 - "$J" "$L" <<'PY'
import json,sys
j,label=sys.argv[1],sys.argv[2]
ids=set()
for line in open(j):
    try: e=json.loads(line)
    except: continue
    if e.get('type')=='started' and e.get('label')==label: ids.add(e['agentId'])
    elif e.get('type') in ('result','failed') and e.get('agentId') in ids:
        print(e['type']); break
PY
)
  if [ -n "$r" ]; then echo "$L in $(basename $(dirname $J)): $r"; exit 0; fi
  sleep 20
done
