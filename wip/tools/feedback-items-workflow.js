export const meta = {
  name: 'resume-feedback-items',
  description: 'Continue interrupted feedback-round items in their existing worktrees (or review finished ones), then adversarially review and fix each',
  phases: [
    { title: 'Implement', detail: 'continue or start each item in its worktree' },
    { title: 'Review', detail: 'independent review lenses per item' },
    { title: 'Fix', detail: 'fix confirmed problems, up to two rounds' },
    { title: 'Follow-up', detail: 'dependent item built on a finished branch' },
  ],
}
const SCRATCH = '/tmp/claude-0/-home-user-mod/1fc86fb0-df36-5d9a-b92a-ac321384cf5d/scratchpad'
const BACKUP = `${SCRATCH}/wip-backup`
// The user asked for one review round per item (then one fix) to save usage.
const MAX_ROUNDS = 1
const RULES = `
RULES FOR EVERY TASK
- PROJECT: Terrahaul, a NeoForge 1.21.1 world-generation mod (Java 21). The Gradle project is the terrahaul/
  subdirectory of the repository / worktree (run ./gradlew from there). Integration branch: claude/exciting-dirac-ajvmvn.
- Resources are tight: 4 CPUs and 15 GB of memory shared by about 13 agents. Run Gradle as ./gradlew --no-daemon ...
  so no daemons linger; do not run two builds at once yourself; stop any process you start. Do NOT run Minecraft
  (runServer/runClient/runData) unless your task explicitly allows it. Remove any extra git worktree you create when
  you are done (git worktree remove --force <dir>).
- Deterministic: pure functions of position and seed; plans identical in every chunk that builds part of them; writes
  inside the 3x3 chunk area a feature may write. No floating blocks, no water walls (still water facing air), no leaks.
- Pure-Java devtools live in terrahaul/src/devtools/java/com/terrahaul/devtools/ (read each main class header for its
  modes; gradle tasks in terrahaul/build.gradle). Compile with ./gradlew --no-daemon compileJava compileDevtoolsJava -q.
- Other jobs change other parts of the code at the same time (see your brief); keep your edits in your own area.
- Do not push. Commit on your branch; commit messages end with these two lines:
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01P4U2ge3L4SazHBdDSoJUZ9
- Match the code's style: Javadoc and comments explaining why, in plain English. No model names anywhere.
- Images: PNG renders under your worktree's terrahaul/build/ or under ${SCRATCH}; always return absolute paths.
`
const REPORT = {
  type: 'object',
  properties: {
    branch: { type: 'string' },
    commit: { type: 'string' },
    worktree: { type: 'string' },
    summary: { type: 'string' },
    images: { type: 'array', items: { type: 'string' }, description: 'absolute paths, before_/after_ prefixes' },
    checks: { type: 'string', description: 'what was measured/checked and the results, and what was not verified' },
  },
  required: ['branch', 'commit', 'worktree', 'summary', 'images', 'checks'],
}
const ISSUES = {
  type: 'object',
  properties: {
    problems: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          title: { type: 'string' },
          severity: { type: 'string', enum: ['blocker', 'major', 'minor'] },
          evidence: { type: 'string' },
          fix: { type: 'string' },
        },
        required: ['title', 'severity', 'evidence', 'fix'],
      },
    },
  },
  required: ['problems'],
}
function how(t) {
  if (t.mode === 'continue') {
    return `A previous agent started this task in the existing worktree ${t.worktree} (branch ${t.branch}, based on ${t.base})
and was stopped by a usage limit before it committed. Its uncommitted work is still there (git -C ${t.worktree} status;
git -C ${t.worktree} diff). cd there and continue: read what it did, judge it against the request, keep what is good,
finish the task and commit on ${t.branch}. Do not create another worktree or branch. (A backup of the interrupted work
is in ${BACKUP}.)`
  }
  if (t.mode === 'fix') {
    return `Reviewers found the problems listed in ${t.problemsFile} (JSON; read it) in the work on branch ${t.branch}. A
previous fixer started addressing them in the existing worktree ${t.worktree} and was stopped by a usage limit; its
uncommitted work is still there (git -C ${t.worktree} status / diff; the last commit is what the reviewers saw). cd
there and finish: fix every blocker and major problem (and the minor ones that are cheap and clearly right), keeping
what the previous fixer did where it is good. Do not create another worktree or branch.`
  }
  if (t.mode === 'fresh') {
    return `Work in the existing worktree ${t.worktree} on branch ${t.branch} (cd there; it is clean at ${t.base}). A previous
attempt was stopped by a usage limit before it changed any source; start the task from the beginning there.`
  }
  return `Create a new branch ${t.branch} from commit ${t.base} in your worktree (git checkout -b ${t.branch} ${t.base}).`
}
async function implement(t, ph) {
  if (t.mode === 'review') return t.report
  return agent(`${RULES}\n${t.brief}\n\n${how(t)}\n\nSTEPS:\n${t.steps}`,
    { label: `implement:${t.key}`, phase: ph, schema: REPORT, ...(t.mode === 'new' ? { isolation: 'worktree' } : {}) })
}
async function reviewFix(rep, t, ph) {
  if (!t.lenses || t.lenses.length === 0) return { final: rep, problems: [], open: [] }
  const history = []
  for (let round = 1; round <= MAX_ROUNDS; round++) {
    const reviews = await parallel(t.lenses.map(([name, lens]) => () => agent(`${RULES}\n${t.brief}

YOUR TASK: adversarially review a finished change (round ${round}). The change is in worktree ${rep.worktree} on branch
${t.branch}; compare against ${t.base} (git -C ${rep.worktree} diff ${t.base}..HEAD). Do not modify that worktree; put
your renders and any baseline checkout in your own directory ${SCRATCH}/review-${t.key}-${name}-${round} (e.g.
git worktree add there for ${t.base}; remove it when done). Try hard to find real problems and report only problems
you have evidence for (image paths, numbers, file:line).
${round > 1 ? `Problems found in the previous round, which the fixer says are fixed - check each really is, and look for new ones: ${JSON.stringify(history[history.length - 1])}\n` : ''}${lens}
Implementer's report: ${JSON.stringify(rep)}`,
      { label: `review:${t.key}:${name}:${round}`, phase: 'Review', schema: ISSUES })))
    const all = reviews.filter(Boolean).flatMap(r => r.problems)
    const serious = all.filter(p => p.severity !== 'minor')
    history.push(all)
    log(`${t.key} round ${round}: ${serious.length} blocker/major, ${all.length - serious.length} minor (${reviews.filter(Boolean).length}/${t.lenses.length} reviews returned)`)
    if (serious.length === 0) return { final: rep, problems: history, open: [] }
    const fixed = await agent(`${RULES}\n${t.brief}

YOUR TASK: fix the problems the reviewers found. Work in the existing worktree ${rep.worktree} on branch ${t.branch}
(cd there; no new branch). Fix every blocker/major problem; re-render and re-run the relevant checks; commit; return
the report (images: the new renders).
Problems: ${JSON.stringify(serious, null, 2)}
Minor ones (fix if cheap and clearly right): ${JSON.stringify(all.filter(p => p.severity === 'minor'))}`,
      { label: `fix:${t.key}:${round}`, phase: 'Fix', schema: REPORT })
    if (fixed) rep = fixed
    if (round === MAX_ROUNDS) return { final: rep, problems: history, open: serious, note: `fixed after round ${round} without a further review` }
  }
  return { final: rep, problems: history }
}
const results = await pipeline(args.tasks,
  t => implement(t, 'Implement'),
  (rep, t) => rep ? reviewFix(rep, t, 'Review') : { key: t.key, error: 'implementation did not return' },
  async (res, t) => {
    if (!t.next || !res || !res.final) return { key: t.key, ...res }
    const n = { ...t.next, base: res.final.commit, mode: 'new' }
    const rep2 = await agent(`${RULES}\n${n.brief}\n\n${how(n)}\n\nSTEPS:\n${n.steps}`,
      { label: `implement:${n.key}`, phase: 'Follow-up', schema: REPORT, isolation: 'worktree' })
    const res2 = rep2 ? await reviewFix(rep2, n, 'Follow-up') : { error: 'follow-up implementation did not return' }
    return { key: t.key, ...res, next: { key: n.key, ...res2 } }
  })
return results