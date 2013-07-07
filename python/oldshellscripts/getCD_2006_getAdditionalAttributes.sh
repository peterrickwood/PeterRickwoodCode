#!/usr/bin/python

import os
import sys



os.system("wget -O tmp.xls \"http://www.censusdata.abs.gov.au/ABSNavigation/download?format=xls&collection=Census&period=2006&productlabel=Dwelling%20Structure%20(includes%20Visitors%20only%20households)&producttype=Census%20Tables&method=Location%20on%20Census%20Night&areacode="+sys.argv[1]+"\"")
#os.system("wget -O tmp.xls \"http://www.censusdata.abs.gov.au/ABSNavigation/download?format=xls&collection=Census&period=2006&productlabel=Dwelling%20Structure&producttype=Census%20Tables&method=Location%20on%20Census%20Night&areacode="+sys.argv[1]+"\"")
os.system("xlhtml -asc -xp:1 tmp.xls > tmp.txt")



def getIndex(lines, matchstr):
	for i in xrange(0, len(lines)):
		if(lines[i].find(matchstr) >= 0):
			return i
	raise Exception("Couldnt find match for string "+matchstr)


#ok, now do the extraction
lines = open("tmp.txt").readlines()

sepi = getIndex(lines, "Separate house")
numsep = int(lines[sepi].split()[-1])
semii = getIndex(lines, "Semi-detached, row or terrace house, townhouse")
numsemi = int(lines[semii+3].split()[-1])
flati = getIndex(lines, "Flat, unit or apartment:")
totalflats = int(lines[flati+5].split()[-1])
numhighrise = int(lines[flati+3].split()[-1])
numlowrise = totalflats-numhighrise

print
print "DWELLCOUNTS: "+sys.argv[1]+" "+str(numsep)+" "+str(numsemi)+" "+str(numlowrise)+" "+str(numhighrise)










