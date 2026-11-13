#!/usr/bin/env python3
"""Focused JVM checks; does not replace the full Android framework suite."""
from pathlib import Path
import subprocess
import os,shutil
r=Path(__file__).resolve().parents[1]
subprocess.run([str(r/'gradlew'),':app:compileDebugUnitTestJavaWithJavac'],cwd=r,check=True)
cache=Path(os.environ.get('GRADLE_USER_HOME',str(Path.home()/'.gradle')))/'caches/modules-2/files-2.1'
sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk:
 sdk=next(line.split('=',1)[1].strip() for line in (r/'local.properties').read_text().splitlines() if line.startswith('sdk.dir='))
cp=[r/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',r/'app/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes',r/'extractor/build/libs/extractor.jar',r/'nanojson/build/libs/nanojson.jar',Path(sdk)/'platforms/android-37.0/android.jar']
for group in ['junit','org.hamcrest','com.squareup.okhttp3','com.squareup.okio','org.jetbrains.kotlin','org.jsoup','com.google.code.gson','org.mozilla','org.mozilla.rhino','com.google.protobuf','com.google.guava']:
 cp.extend((cache/group).rglob('*.jar'))
cmd=[str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else shutil.which('java'),'-cp',':'.join(map(str,cp)),'org.junit.runner.JUnitCore']
cmd+=['app.cadence.'+x for x in ['AudioSourceCacheTest','ArtworkTest','ListeningTrackerTest','RecommendationsTest','MusicCatalogRoutingTest']]
p=subprocess.run(cmd,text=True,capture_output=True);print(p.stdout,p.stderr)
report=r/'validation/0.9.1';report.mkdir(parents=True,exist_ok=True);(report/'core-model-tests.txt').write_text(p.stdout+p.stderr)
raise SystemExit(p.returncode)
