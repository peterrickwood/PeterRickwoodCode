import sys


sys.stderr.write("this utility takes a file with lines in the format\n")
sys.stderr.write("LAYER <data ordered by long>\n")
sys.stderr.write("LAYER <data ordered by long>\n")
sys.stderr.write("LAYER <data ordered by long>\n")
sys.stderr.write("LAYER <data ordered by long>\n")
sys.stderr.write("\n")
sys.stderr.write("and produces a file suitable for making a GISLayer\n")

sys.stderr.write("\n")
sys.stderr.write("args are:\n")
sys.stderr.write("1) raw data file 2) minlat 3) maxlat 4) minlong 5) maxlong\n")
sys.stderr.write("\n")

layerlines = open(sys.argv[1]).readlines()
lines = [" ".join(item.split()[1:]) for item in layerlines]


print sys.argv[2]+" "+sys.argv[3]+" "+sys.argv[4]+" "+sys.argv[5]
print len(lines[0].split()) #columns
print len(lines) #rows
for item in lines:
	print item


