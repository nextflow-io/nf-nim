Needs to be a process instead of a function because it's not just a quick call and return, it could take 2 minutes to run.

Pros

| Local     | exec:                                | Executor                   |
| --------- | ------------------------------------ | -------------------------- |
| It's done | Same as local but no curl dependency | Log the container, version |
|           |                                      | easy queue throttling      |
|           |                                      | Can log the server api version|

Cons

| Local | exec: | Executor   |
| ----- | ----- | ---------- |
|       |       | Complexity |
