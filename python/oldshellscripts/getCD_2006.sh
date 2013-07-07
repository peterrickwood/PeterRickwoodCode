#!/usr/bin/python

import sys
import os

def safeint(intstring):
        return int("".join(intstring.strip().split(",")))


cd = sys.argv[1]

url ="http://www.censusdata.abs.gov.au/ABSNavigation/prenav/ProductSelect?newproducttype=QuickStats&btnSelectProduct=View+QuickStats+%3E&collection=Census&period=2006&areacode="+cd+"&geography=&method=&productlabel=&producttype=&topic=&navmapdisplayed=false&javascript=false&breadcrumb=LP&topholder=0&leftholder=0&currentaction=201&action=401&textversion=true" 


cmdstr = "wget --timeout=8 -O tmpfl \""+url+"\""

os.system("\\rm tmpfl")
os.system(cmdstr)

tmpfllines=open("tmpfl").readlines()

#for line in tmpfllines:
#	print line


lowpopcountlines = [item for item in tmpfllines if item.find("very low population count") >= 0]
if(len(lowpopcountlines) > 0):
	print "CD "+cd+" has -1 total persons (excl. O/S vis) and -1 including"
	sys.exit()


totalpersonsline = [item for item in tmpfllines if item.find("Total persons (excluding overseas visitors)") >= 0]
if(len(totalpersonsline) != 1):
	raise Exception("Could not get line for total persons for CD "+cd)
osvisitorsline = [item for item in tmpfllines if item.find("Overseas visitors (excluded from all other classifications)") >= 0]
if(len(osvisitorsline) != 1):
	pass #raise Exception("Could not get line for o/s visitors for CD "+cd)

matchstr = "<div align=\"right\">"

starti = totalpersonsline[0].find(matchstr)+len(matchstr)
endi = totalpersonsline[0].find("</div>", starti)
tmpstr = totalpersonsline[0][starti:endi]
totp = safeint(tmpstr)

if(len(osvisitorsline) != 1):
	osv = 0
else:
	starti = osvisitorsline[0].find(matchstr)+len(matchstr)
	endi = osvisitorsline[0].find("</div>", starti)
	tmpstr = osvisitorsline[0][starti:endi]
	osv = safeint(tmpstr)

print "CD "+cd+" has "+str(totp)+" total persons (excl. O/S vis) and "+str(totp+osv)+" including"

#print totalpersonsline
#print osvisitorsline




