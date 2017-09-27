#!/usr/bin/python
import sys,subprocess,os

cmd = "java -jar " + os.path.dirname(__file__) + "/target/oneops-inventory-1.0.0-SNAPSHOT-jar-with-dependencies.jar " + sys.argv[1]
if len(sys.argv) == 3:
    cmd = cmd + " " + sys.argv[2]

p = subprocess.Popen(cmd, stdout=subprocess.PIPE, shell=True)

(output, err) = p.communicate()
p_status = p.wait()

print output
sys.exit(p_status)
