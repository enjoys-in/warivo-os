#!/usr/bin/env python3
"""
A standing-in-for-a-compiler check for the Warivo Launcher sources.

The launcher has never been compiled (see GUIDE.me), so this catches the classes of
mistake that are cheap to make while writing a lot of Kotlin blind:

  * unbalanced braces and parentheses,
  * capitalised identifiers used without an import or a same-package declaration,
  * top-level Compose extension functions used without their import,
  * `com.warivo.os.*` imports that point at something that does not exist.

What it CANNOT catch, and why the first real build still matters: wrong overloads, a
Material icon that moved between icon-set releases, a MapLibre API rename, type errors,
and anything about Compose's runtime behaviour.

Usage:
    python3 tools/check_kotlin.py [source-root]

Exits non-zero when it finds something, so it works in a pre-commit hook or CI.
"""

import io, os, re, sys
root = sys.argv[1] if len(sys.argv) > 1 else "android/warivo-launcher/app/src/main/java"
files=sorted(os.path.join(dp,f) for dp,_,fn in os.walk(root) for f in fn if f.endswith(".kt"))

# Top-level Compose/Kotlin functions that genuinely need an import when used.
# NOTE: weight/align/matchParentSize are RowScope/ColumnScope/BoxScope MEMBERS — they are
# receiver methods, not top-level, so they must not be listed here.
NEEDS_IMPORT = """fillMaxSize fillMaxWidth fillMaxHeight padding size width height widthIn heightIn
sizeIn aspectRatio clip background border alpha clickable verticalScroll horizontalScroll
rememberScrollState offset rotate scale graphicsLayer zIndex wrapContentSize
defaultMinSize asImageBitmap collectAsStateWithLifecycle painterResource stringResource
produceState remember derivedStateOf mutableStateOf rememberCoroutineScope withContext
delay launch""".split()

def strip(src):
    src=re.sub(r'"""(?:.|\n)*?"""','""',src); src=re.sub(r'"(?:\\.|[^"\\\n])*"','""',src)
    src=re.sub(r'//[^\n]*','',src); src=re.sub(r'/\*(?:.|\n)*?\*/','',src); return src

pkg_decls={}
for p in files:
    s=io.open(p,encoding="utf-8").read()
    pkg=re.search(r'^package\s+([\w.]+)',s,re.M).group(1)
    d=pkg_decls.setdefault(pkg,set())
    for pat in (r'(?:class|object|interface|enum class|typealias)\s+(\w+)',
                r'fun\s+(?:<[^>]+>\s+)?(?:\w+\.)?(\w+)\s*\(',
                r'(?:val|var)\s+(\w+)', r'^\s*(\w+)\s*[,(]'):
        for m in re.finditer(pat,s,re.M): d.add(m.group(1))

builtin=set("""String Int Long Float Double Boolean Unit Any Nothing List Map Set Pair Triple
Exception RuntimeException SecurityException IllegalStateException Throwable Array ArrayDeque
Modifier Color Composable Math System Build Bundle Context Intent IntentFilter ComponentName
Charsets UUID Locale Date SimpleDateFormat Calendar Iterable Comparable Enum Companion Suppress
Deprecated JvmStatic Volatile Synchronized IntArray ByteArray FloatArray CharSequence StringBuilder
Thread Runnable Handler Looper Log R Warivo Telemetry Trip Track Panel Location Ride
JSONArray JSONObject Bitmap Size ImageBitmap""".split())

bad=0
for p in files:
    s=io.open(p,encoding="utf-8").read()
    rel=os.path.relpath(p,root)
    pkg=re.search(r'^package\s+([\w.]+)',s,re.M).group(1)
    imported={m.group(1).split(".")[-1] for m in re.finditer(r'^import ([\w.]+)',s,re.M)}
    local=pkg_decls.get(pkg,set())
    body=strip(s)
    for o,c in (("{","}"),("(",")")):
        d=body.count(o)-body.count(c)
        if d: print(f"UNBALANCED {o}{c} {d:+d} {rel}"); bad+=1
    code=re.sub(r'^(?:package|import).*$','',body,flags=re.M)
    for m in re.finditer(r'(?<![\w.])([A-Z][A-Za-z0-9_]{2,})\b', code):
        n=m.group(1)
        if n in imported or n in local or n in builtin or re.match(r'^[A-Z0-9_]+$',n): continue
        print(f"UNRESOLVED TYPE {n:24} {rel}"); bad+=1
    for name in NEEDS_IMPORT:
        if re.search(r'\b'+name+r'\s*\(', code) and name not in imported and name not in local:
            print(f"MISSING IMPORT  {name:24} {rel}"); bad+=1
    for m in re.finditer(r'^import (com\.warivo\.os\.[\w.]+)',body,re.M):
        sym=m.group(1); name=sym.split(".")[-1]; owner=".".join(sym.split(".")[:-1])
        if name not in pkg_decls.get(owner,set()) and not sym.endswith(".R"):
            print(f"BAD IMPORT {sym} {rel}"); bad+=1
tot=sum(len(io.open(f,encoding='utf-8').read().splitlines()) for f in files)
print(f"{len(files)} files  {tot} lines  {bad} problems")
sys.exit(1 if bad else 0)
