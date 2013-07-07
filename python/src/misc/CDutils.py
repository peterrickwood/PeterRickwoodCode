
import sys
import os
import string
import math
import random
import numpy



sydbounds=((-33.4,-34.25),(150.7, 151.5))
melbbounds=((-37.55,-38.5 ),(144.25, 145.6))
brisbounds=((-27.28,-27.7),(152.95, 153.4))
perthbounds=((-31.65,-32.6), (115.6, 116.05))
adelaidebounds=((-34.66,-35.2),(138.45,138.75))
canberrabounds=((-35.15,-35.48),(149.0,149.175))
wgongbounds=((-34.309,-34.6),(150.76,150.975))
newcastlebounds=((-32.865,-33.125),(151.55,151.8))

melbcbd=(-37.814, 144.963)    #cnr bourke and Elizabeth sts. At start of Bourke Street Mall. Old GPO
briscbd=(-27.47,153.026)
perthcbd=(-31.9554, 115.8556)
adelaidecbd=(-34.926, 138.6)
canberracbd=(-35.2787, 149.13)  #near civic bus interchange
wgongcbd=(-34.425, 150.894) #(mall est of woolongong rail station)
newcastlecbd=(-32.928,151.7725) #(south of Civic station)









nsyd=(-33.821843, 151.2056)
centrepoint=(-33.870178, 151.208743)
bronte=(-33.900803, 151.267070)
nearhornsby=(-33.718465, 151.118648)
epping=(-33.776457, 151.070286)
penhills=(-33.765866,151.038392)
newtown=(-33.897752,151.178744)
strathfield=(-33.877648,151.103497)
stleonards=(-33.822382,151.194184)
chatswood=(-33.796250,151.180796)
m5entrance=(-33.936031,151.111135) #entrance before airport to city
m5casula=(-33.938322,150.907522)
panania=(-33.949234,150.996649) #inbetween m5 and rail 
m4wentworthville=(-33.812736,150.950021)
nearsydenham=(-33.9284445,151.155855) #sw of sydenham, where rail lines merge


VERY_VERBOSE=3
VERBOSE=2
NORMAL=1
QUIET=0
debuglvl=NORMAL
def dprint(str, lvl):
	if(debuglvl >= lvl):
		print str




def test():
	cds = analyzeTransect0(nsyd, nearhornsby, 0.01)
	print "Sorted keys are:"
	keys=cds[cds.keys()[0]].keys()
	keys.sort()
	print keys
	elem = cds[cds.keys()[0]]
	nkeys = len(elem.keys())
	var1 = elem.keys()[int(math.floor(random.uniform(0, nkeys)))]	
	var2 = elem.keys()[int(math.floor(random.uniform(0, nkeys)))]	
	showPlot(cds, var1, var2)
	var1 = elem.keys()[int(math.floor(random.uniform(0, nkeys)))]	
	var2 = elem.keys()[int(math.floor(random.uniform(0, nkeys)))]	
	showFilterPlot(cds, var1, var2, 0.1)
	return cds
	

def getCityCDs(cityname_str):
	if(cityname_str == "canberra"):
		bounds = canberrabounds
		cbd = canberracbd
	elif(cityname_str == "newcastle"):
		bounds = newcastlebounds
		cbd = newcastlecbd
	elif(cityname_str == "wgong"):
		bounds = wgongbounds
		cbd = wgongcbd
	else:
		raise("City name "+cityname_str+" not supported... the state capitals have their own functions, try one of them if thats what you are after -- i.e. getSydneyCDs()")		


	loadCDs(bounds[0][0], bounds[0][1], bounds[1][0], bounds[1][1])
	cdnums = cddat.keys()
	numcds=len(cdnums)
	done=0
	for key in cdnums:
		lat = cddat[key]['lat']
		lon = cddat[key]['long']
		if(lat <= bounds[0][0] and lat >= bounds[0][1] and lon >= bounds[1][0] and lon <= bounds[1][1]):
			extractInfo(key)
			cddat[key]['dist_from_cbd'] = getDistance(cbd[0], cbd[1], cddat[key]['lat'],cddat[key]['long'])
			done = done+1
			pctdone = done/float(numcds)
			print 'pct done: '+str(pctdone)



def getAllPerthCDs():
	loadCDs(perthbounds[0][0], perthbounds[0][1], perthbounds[1][0], perthbounds[1][1])
	cdnums = cddat.keys()
	numcds=len(cdnums)
	done=0
	for key in cdnums:
		lat = cddat[key]['lat']
		lon = cddat[key]['long']
		if(isInPerth(lat, lon)):
			extractInfo(key)
			cddat[key]['dist_from_cbd'] = getDistance(perthcbd[0], perthcbd[1], cddat[key]['lat'],cddat[key]['long'])
			done = done+1
			pctdone = done/float(numcds)
			print 'pct done: '+str(pctdone)


def getAllAdelaideCDs():
	loadCDs(adelaidebounds[0][0], adelaidebounds[0][1], adelaidebounds[1][0], adelaidebounds[1][1])
	cdnums = cddat.keys()
	numcds=len(cdnums)
	done=0
	for key in cdnums:
		lat = cddat[key]['lat']
		lon = cddat[key]['long']
		if(isInAdelaide(lat, lon)):
			extractInfo(key)
			cddat[key]['dist_from_cbd'] = getDistance(adelaidecbd[0], adelaidecbd[1], cddat[key]['lat'],cddat[key]['long'])
			done = done+1
			pctdone = done/float(numcds)
			print 'pct done: '+str(pctdone)


def getAllBrisCDs():
	loadCDs(brisbounds[0][0], brisbounds[0][1], brisbounds[1][0], brisbounds[1][1])
	cdnums = cddat.keys()
	numcds=len(cdnums)
	done=0
	for key in cdnums:
		lat = cddat[key]['lat']
		lon = cddat[key]['long']
		if(isInBris(lat, lon)):
			extractInfo(key)
			cddat[key]['dist_from_cbd'] = getDistance(briscbd[0], briscbd[1], cddat[key]['lat'],cddat[key]['long'])
			done = done+1
			pctdone = done/float(numcds)
			print 'pct done: '+str(pctdone)

def getAllMelbCDs():
	loadCDs(melbbounds[0][0], melbbounds[0][1], melbbounds[1][0], melbbounds[1][1])
	cdnums = cddat.keys()
	numcds=len(cdnums)
	done=0
	for key in cdnums:
		lat = cddat[key]['lat']
		lon = cddat[key]['long']
		if(isInMelb(lat, lon)):
			extractInfo(key)
			cddat[key]['dist_from_cbd'] = getDistance(melbcbd[0], melbcbd[1], cddat[key]['lat'],cddat[key]['long'])
			done = done+1
			pctdone = done/float(numcds)
			print 'pct done: '+str(pctdone)
	

#for testing
def getSomeSydneyCDs(toget):
	loadCDs(sydbounds[0][0], sydbounds[0][1], sydbounds[1][0], sydbounds[1][1])
	numcds=len(toget)
	done=0
	for key in toget:
		lat = cddat[key]['lat']
		lon = cddat[key]['long']
		if(isInSydney(lat,lon)):
			extractInfo(key)
			cddat[key]['dist_from_cbd'] = getDistance(centrepoint[0], centrepoint[1], cddat[key]['lat'],cddat[key]['long'])
			done = done+1
			pctdone = done/float(numcds)
			print 'pct done: '+str(pctdone)

	
def getAllSydneyCDs():
	loadCDs(sydbounds[0][0], sydbounds[0][1], sydbounds[1][0], sydbounds[1][1])
	cdnums = cddat.keys()
	numcds=len(cdnums)
	done=0
	for key in cdnums:
		lat = cddat[key]['lat']
		lon = cddat[key]['long']
		if(isInSydney(lat,lon)):
			extractInfo(key)
			cddat[key]['dist_from_cbd'] = getDistance(centrepoint[0], centrepoint[1], cddat[key]['lat'],cddat[key]['long'])
			done = done+1
			pctdone = done/float(numcds)
			print 'pct done: '+str(pctdone)
	


def getClosestCDCentroid(lat, lon):
	bestdist = 99999999
	bestcd = -1
	for cdnum in cddat.keys():
		cdlat = cddat[cdnum]['lat']
		cdlon = cddat[cdnum]['long']
		dist = greatCircDist(lat, lon, cdlat, cdlon)
		if(dist < bestdist):
			bestdist = dist
			bestcd = cdnum
	return bestcd	
		

	

#work out which CDs belong to which LGA
lgatocdmap=dict()
def getLGAInfo():
	if(len(lgatocdmap.keys()) > 0):
		return lgatocdmap
	f=open('/home/peterr/data/mapinfodat/All Maps/CDs/NSWcd01.MID')
	cdlines = f.readlines()
	f.close()
	for line in cdlines:
		lganame=" ".join(line.split(',')[-2].strip()[1:-1].split()[0:-1])
		lganame = lganame.lower()
		if(not(lgatocdmap.has_key(lganame))):
			lgatocdmap[lganame] = []
		lgatocdmap[lganame].append(int(line.split(',')[0]))

	#now go and add this information to each CD
	for lganame in lgatocdmap.keys():
		cdlist = lgatocdmap[lganame]
		for item in cdlist:
			if(cddat.has_key(item)):
				cddat[item]['lga'] = lganame

	#we also return the list of lga -> CDnum mappings.
	return lgatocdmap
		
				
#returns a dictionary with lganame->value
#mappings, where value is the the sum of all the CDs in that lga
#
#If all the CDs that make up the LGA are not available, no
#sum is recorded for that LGA
def getLGALevelStatistic(statkey):
	raw_input("WARNING: getting LGA level statistics in this way only works for strictly additive statistics (like population and households). Other values will be WRONG. Press enter to confirm you understand this.")
	lgas = getLGAInfo()
	res = {}
	for lganame in lgas.keys():
		res[lganame] = 0.0
		cdlist = lgas[lganame]
		for cd in cdlist:
			if(not(cddat.has_key(cd))):
				print "We are missing CD data ("+str(cd)+") for LGA "+lganame
				res[lganame] = None
				break
			if(cddat[cd].has_key(statkey)):
				res[lganame] = res[lganame]+cddat[cd][statkey]
			else:	
				print "We are missing "+statkey+" data for CD ("+str(cd)+") that is part of LGA "+lganame+" continuing anyway...."
				#res[lganame] = None
				#break
	return res
	



#get the CD centroid that is closest to (lat,lon)
def getClosestCD(lat, lon):
	
	bestd = 999999999
	bestcd = -1
	for cdnum in cddat.keys():
		cdlat = cddat[cdnum]['lat']
		cdlon = cddat[cdnum]['long']
		dist = getDistance(cdlat, cdlon, lat, lon)
		if(dist < bestd):
			bestd = dist
			bestcd = cdnum
	return bestcd





#get the LGA that is (lat, lon) belongs to
def getLGA(lat, lon):
	getLGAInfo()

	cd = getClosestCD(lat, lon)
	return cddat[cd]['lga']
		
			








def dumpMultiDataToFile(keylist):
	cdnums = cddat.keys()
	headerline = "#"+" ".join(keylist)+"\n"
	lines=[headerline]
	for num in cdnums:
		valstr = ""
		lat = cddat[num]['lat']
		long = cddat[num]['long']
		for key in keylist:
			if(cddat[num].has_key(key)):
				val = cddat[num][key]
				#if(lat >= -34.1 and lat <= -33.6 and long >= 150.6 and long <= 151.34):
			else:
				val = "NA"
			valstr = valstr+str(val)+" "

		if(len(valstr) > 0):
			lines.append(str(lat)+" "+str(long)+" "+valstr+"\n")

	print "there are "+str(len(lines))+" data points to be dumped"	
	filename = "dump_"+key+".dat"	
	print "dumping to "+filename
	f=open(filename, "w")
	f.writelines(lines)
	f.close()
	

def dumpDataToFile(key):
	if(type(key) == type([])): #list of keys
		dumpMultiDataToFile(key)
		return
	cdnums = cddat.keys()
	lines=[]
	for num in cdnums:
		if(cddat[num].has_key(key)):
			lat = cddat[num]['lat']
			long = cddat[num]['long']
			val = cddat[num][key]
			#if(lat >= -34.1 and lat <= -33.6 and long >= 150.6 and long <= 151.34):
			lines.append(str(lat)+" "+str(long)+" "+str(val)+"\n")

	print "there are "+str(len(lines))+" data points to be dumped"	
	filename = "dump_"+key+".dat"	
	print "dumping to "+filename
	f=open(filename, "w")
	f.writelines(lines)
	f.close()




def isInSydney(lat, lon):
	return (lat <= sydbounds[0][0] and lat >= sydbounds[0][1] and lon >= sydbounds[1][0] and lon <= sydbounds[1][1]) 

def isInMelb(lat, lon):
	return (lat <= melbbounds[0][0] and lat >= melbbounds[0][1] and lon >= melbbounds[1][0] and lon <= melbbounds[1][1]) 

def isInBris(lat, lon):
	return (lat <= brisbounds[0][0] and lat >= brisbounds[0][1] and lon >= brisbounds[1][0] and lon <= brisbounds[1][1]) 

def isInPerth(lat, lon):
	return (lat <= perthbounds[0][0] and lat >= perthbounds[0][1] and lon >= perthbounds[1][0] and lon <= perthbounds[1][1]) 

def isInAdelaide(lat, lon):
	return (lat <= adelaidebounds[0][0] and lat >= adelaidebounds[0][1] and lon >= adelaidebounds[1][0] and lon <= adelaidebounds[1][1]) 


#handles conversion of strings that may have commas (like 1,234)
def safeint(intstring):
	return int("".join(intstring.strip().split(",")))



#case sensitive
def noncompleteRecord(lines):
	
	if(lines[6].find("Catalogue No. 2001.0") >= 0):
		return True
	return False #as far as we know


#case insensitive
def matchLine(lines, matchstring):
	filteredlines = [item for item in lines if item.lower().find(matchstring.lower()) != -1]
	if(len(filteredlines) == 0):
		raise Exception("No match found with "+matchstring)
	elif(len(filteredlines) > 1):
		raise Exception("Multiple matches found with "+matchstring)
	return filteredlines[0] #only 1 match
	



#extract the URL for a census district from a line of HTML
def extractBCPURLfromHTML(*lineparts):
	line=" ".join(list(lineparts))
	#make sure the text is the href tag
	hrefstart = line.find("href")
	if(hrefstart < 0):
		raise Exception("cannot parse URL from HTML text")

	#find the first and last quotes
	openquote=line.find("\"", hrefstart)
	endquote=line.find("\"", openquote+1)
	url=line[openquote+1:endquote]
	print "http://www.abs.gov.au"+url


#extract the URL for an LGA from a line of HTML
def extractLGAfromHTML(*lineparts):
	line=" ".join(list(lineparts))
	#make sure the text is the href tag
	hrefstart = line.find("href")
	if(hrefstart < 0):
		raise Exception("cannot parse URL from HTML text")

	#find the first and last quotes
	openquote=line.find("\"", hrefstart)
	endquote=line.find("\"", openquote+1)
	url=line[openquote+1:endquote]
	print "http://www.abs.gov.au"+url







def getDataDir(cd_int):
	base=os.sep+'home'+os.sep+'peterr'+os.sep+'data'+os.sep+'abs_stats'+os.sep+'2001census'
	res=base+os.sep+"BCP_"+str(cd_int)+"_pages"
	#print res
	return res



def findPosition(numlist, postofind):
	#print "finding "+str(postofind)+" in "+str(numlist)
	if(postofind > 1 or postofind < 0):
		raise Exception("2nd argument must be between 0 and 1")

	total=sum(numlist)
	tofind=postofind*total
	#print "finding "+str(tofind)+"th element in "+str(numlist)
	if(tofind==total):
		return len(numlist)-0.9999
	tmp=0
	tmpi=0
	while(1):
		tmp=tmp+numlist[tmpi]
		if(tmp >= tofind):
			break
		tmpi = tmpi+1

	#ok, so tmpi is the index of the element that contains
	#the value we want. 
	tmp=tmp-numlist[tmpi]
	offset = (tofind-tmp)/numlist[tmpi]
	#print "tmpi+offset is "+str(tmpi+offset)
	return (tmpi+offset)


def findMedian(numlist):
	return findPosition(numlist, 0.5)



def getMedianValueInSortedList(sortedlist):
	if(len(sortedlist) % 2 == 1):
		return sortedlist[len(sortedlist)/2]
	else:
		return (sortedlist[len(sortedlist)/2]+sortedlist[len(sortedlist)/2-1])/2



#dictionary of all CDs with the cdnum as the key and a dictionary as the value.
cddat = dict()

def loadSydneyCDs():
	loadCDs(sydbounds[0][0], sydbounds[0][1], sydbounds[1][0], sydbounds[1][1])

#load all the CD lat/longs from file
def loadCDs(north, south, west, east):

	file=open("Census2001R1_geog_desc.txt")
	cdlines=file.readlines()

	for line in cdlines:
		if(line.split()[0].strip().upper() == "CD"):
			cdnum=int(line.split()[-5].strip())
			if(cddat.has_key(cdnum)):
				continue #cd already loaded
			area=float(line.split()[-3].strip())
			if(area == 0):
				continue
			long=float(line.split()[-2].strip())
			if(long >= west and long <= east):
				lat=float(line.split()[-1].strip())
				if(lat <= north and lat >= south):
					splitline=line.split()
					cddat[cdnum] = {'cdnum' : cdnum, 'lat' : lat, 'long' : long, 'area' : area}
			
			


#given a point on the line, and the gradient, calculate the intercept
def calculateIntercept((x,y), m):
	return y-m*x





#calculate the point of intersection between the given
#line and one perpendicular to it that passes through (x,y)
def calculatePerpendicularIntersection((x1,y1),m,b):
	if(m == 0.0):
		return (x1,b)
	
	#first work out the gradient of the line perpendicular to the line
	perpgrad = -1.0/m

	#now work out the intercept of perpendicular line through point (x1,y1)
	perpintercept = y1-perpgrad*x1

	#now calculate intersect of original line and perpendicular line through point x1,y1
	xp=(perpintercept-b)/(m-perpgrad)
	yp=m*xp+b

	return (xp, yp)
	



#calculate the distance of a point from a line
def calculateDistanceOfPointFromLine((x1,y1),m,b):
	if(m == 0.0): #special case -- line is parallel to the x axis
		return abs(y1-b)

	#calculate point where perpendicular line through point 
	#cuts the original line.
	(xp, yp) = calculatePerpendicularIntersection((x1,y1),m,b)
	
	#now calculate the distance between the points
	return math.sqrt((x1-xp)**2+(y1-yp)**2)
	






#find the distance between lat,long and lat1,long1
def getDistance(lat_str, long_str, lat2_str, long2_str):
	lat1 = float(lat_str)
	long1 = float(long_str)
	lat2 = float(lat2_str)
	long2 = float(long2_str)
	return greatCircDist(lat1, long1, lat2, long2)
#	result = math.sqrt((lat1-lat2)**2 + (long1-long2)**2)

def greatCircDist(lat1, long1, lat2, long2):
        #convert to radians
        latr1 = lat1*math.pi/180.0
        latr2 = lat2*math.pi/180.0
        longr1 = long1*math.pi/180.0
        longr2 = long2*math.pi/180.0

        longdif = longr1-longr2

        #now calctate the haversine formula
        term1 = math.sin(((latr1-latr2)/2))**2
        term2 = math.cos(latr1)*math.cos(latr2)*(math.sin(longdif/2))**2

        angdif = 2*math.asin(math.sqrt(term1+term2))

        #now work out the distance
        earthradius = 6372.795*1000 #in metres

        return earthradius*angdif




def getDistance0((lat,long),(lat2,long2)):
	getDistance(lat, long, lat2, long2)




def analyzeTransect0(a, b, tol):
	return analyzeTransect(a[0], a[1], b[0], b[1], tol)

def analyzeTransect(startlat_str, startlong_str, endlat_str, endlong_str, tol_str):
	startlat = float(startlat_str)
	startlong = float(startlong_str)
	endlat = float(endlat_str)
	endlong = float(endlong_str)
	tol = float(tol_str)

	#get the transect CDs
	cds=getTransectCDs(startlat_str, startlong_str, endlat_str, endlong_str, tol_str)

	#extract info about them
	for cd in cds.keys():
		extractInfo(cd)

	#work out the distance of each transect CD from the start of the transect
	distFromPoint(cds, startlat_str,startlong_str)
	return cds





#like showplot, but filter out extreme values
def showFilterPlot(cds, var1, yvars, filterpct, lstsq=1):
	#if only 1 y variable was used, we bundle it into a 1 element list
	if(type(yvars) != type([])):
		showFilterPlot(cds, var1, [yvars], filterpct, lstsq)
		return

	#make sure that only data with available keys is included
	cdlist = [item for item in cds.values() if item.has_key(var1)]
	for yvar in yvars:
		cdlist = [item for item in cdlist if item.has_key(yvar)]

	if(len(cdlist) == 0):
		raise Exception("no data match specified keys")

	var1vals = [item[var1] for item in cdlist]
	yvarvals = []
	for var in yvars:
		varvals = [item[var] for item in cdlist]
		varvals.sort()
		yvarvals.append(varvals)
	var1vals.sort()

	#now work out bounds that include (1-filterpct) of the data
	pct = filterpct/2.0
	lowindex = int(round(pct*len(var1vals)))
	highindex = int(round((1-pct)*len(var1vals)))-1
	if(highindex < 0):
		highindex = 0

	

	bounds={}
	bounds[var1] = (var1vals[lowindex],var1vals[highindex])

	#tuples of all the bounds on the y variables
	for i in range(0, len(yvars)):	
		low = yvarvals[i][lowindex]
		high = yvarvals[i][highindex]
		bounds[yvars[i]] = (low, high)

	
	#now go through and apply all the bounds filters
	datums = []
	for datum in cdlist:
		passed = 1
		for var in bounds.keys():
			if(datum[var] < bounds[var][0] or datum[var] > bounds[var][1]):
				passed = 0
				break
		if(passed): #passed all bounds tests
			datums.append(datum)

	if(len(datums) == 0):
		print "No data passed filtering!!! Not plotting anything"
		return
	
	
	xlist = [item[var1] for item in datums]
	ylist = []
	for var in yvars:
		ylist.append([item[var] for item in datums])

	showPlot0(var1, xlist, yvars, ylist, lstsq)
	




def showPlot(cds, var1, yvars, lstsq=1):
	#if only 1 y variable was used, we bundle it into a 1 element list
	if(type(yvars) != type([])):
		showPlot(cds, var1, [yvars], lstsq)
		return

	#make sure that only data with available keys is included
	cdlist = [item for item in cds.values() if item.has_key(var1)]
	for yvar in yvars:
		cdlist = [item for item in cdlist if item.has_key(yvar)]

	if(len(cdlist) == 0):
		print "No data passed filtering!!! Not plotting anything"
		return

	xlist = [item[var1] for item in cdlist]
	ylist = []
	for var in yvars:
		ylist.append([item[var] for item in cdlist])
		
 
	showPlot0(var1, xlist, yvars, ylist, lstsq)





#fit a line to the data (least squares fit)
def linsolve(xvals, yvals):
	print 'allocating matrix space'
	A = numpy.zeros((len(xvals), 2), float)
	b = numpy.transpose(numpy.matrix(yvals))
	print 'initializing A and b'
	for i in range(0, len(xvals)):
		A[i][0] = xvals[i]
		A[i][1] = 1.0

	#find least squares solution
	print 'finding least squares solution'
	(x,resids,rank,s) = numpy.linalg.lstsq(A, b, rcond=1.e-10)

	print 'done'
	#return gradient and intercept of that line
	return (x[0,0], x[1,0])
	
	
	




def showPlot0(var1, xdatalist, yvars, ydatalist, lstsq=1):
	if(type(yvars) != type([])):
		raise Exception('y variables must be a list of 1 or more variable names')

	if(len(xdatalist) != len(ydatalist[0])):
		raise Exception('unmatched plot lists')
	datafilename= var1
	for item in yvars:
		datafilename = datafilename+"_vs_"+item
	file=open(datafilename, "w")
	plotcmdsfile=open("plotcmd.tmp", "w")
	lines = []

	if(len(yvars) == 1):
		if(lstsq):
			lsfit = linsolve(xdatalist, ydatalist[0])
	else:
		#go back and rescale the first set of y values
		miny = min(ydatalist[0])
		maxy = max(ydatalist[0])
		for i in range(0, len(ydatalist[0])):
			if(maxy == miny):
				ydatalist[0][i] = 0.0
			else:
				ydatalist[0][i] = (ydatalist[0][i]-miny)/float(maxy-miny)
		if(lstsq):
			lsfit = linsolve(xdatalist, ydatalist[0])
	
	cmdline = "plot \""+datafilename+"\" using 1:2"
	if(lstsq):
		cmdline = cmdline+" , x*"+str(lsfit[0])+"+"+str(lsfit[1])+" title \"least squares "+var1+" vs "+yvars[0]+"\""

	if(len(yvars) > 1):


		for y in range(1, len(yvars)):
			#now rescale all y vars to be between 0 and 1,
			#so that we can compare them
			miny = min(ydatalist[y])
			maxy = max(ydatalist[y])
			for i in range(0, len(ydatalist[y])):
				if(maxy == miny):
					ydatalist[y][i] = 0.0
				else:
					ydatalist[y][i] = (ydatalist[y][i]-miny)/float(maxy-miny)
			cmdline = cmdline + " , \""+datafilename+"\" using 1:"+str(y+2)
		
			if(lstsq):
				lsfit = linsolve(xdatalist, ydatalist[y])
				cmdline = cmdline+" , x*"+str(lsfit[0])+"+"+str(lsfit[1])+" title \"least squares "+var1+" vs "+yvars[y]+"\""	




	for i in range(0, len(xdatalist)):
		dataline = str(xdatalist[i])
		for y in ydatalist:
			dataline = dataline+" "+str(y[i])
		dataline = dataline+'\n'
		lines.append(dataline)

	file.writelines(lines)
	file.close()
	plotcmdsfile.writelines([cmdline+"\n"])
	plotcmdsfile.close()
	os.system("gnuplot plotcmd.tmp -")


		
	



def distFromPoint(cds, lat_str, long_str):
	fixedlat = float(lat_str)
	fixedlong = float(long_str)
	for cd in cds.values():
		lat = cd['lat']
		long = cd['long']
		dist=math.sqrt((lat-fixedlat)**2 + (long-fixedlong)**2)
		cd['dist_from_'+str(lat_str)+'_'+str(long_str)] = dist
		






#find all the CD's that lie within 'tol' of the 
#line running from start to finish
#
#arguments may be string representations of floats or floats
#
#returns a dictionary (indexed by cd number) of all the lines along
#the transect
def getTransectCDs(startlat_str, startlong_str, endlat_str, endlong_str, tol_str):

	startlat = float(startlat_str)
	startlong = float(startlong_str)
	endlat = float(endlat_str)
	endlong = float(endlong_str)
	tol = float(tol_str)

	#make sure basic lat/long info about all the CDs is loaded
	loadCDs(max(startlat, endlat), min(startlat, endlat), min(startlong, endlong), max(startlong, endlong))


	#work out the line perpendicular to the transect
	#
	#This is all done with Euclidean geometry at the moment, not
	#spherical. And it also assumes that 1 degree of latitude is
	#the same distance as 1 degree of longitude, which is not
	#correct at Sydney, obviously, but is a close enough approximation
	#for now.
	#
	#redo in spherical when I get time, but its messy enough in
	#euclidean.
	constantlongitude=0 #special case, where longitude does not vary along transect
	if(endlong == startlong):
		constantlongitude=1
		dprint("transect is along line of constant longitude (has no gradient)", VERBOSE)
	else:	
		grad=(endlat-startlat)/(endlong-startlong)
		dprint("gradient of transect (delta lat w.r.t long) is "+str(grad), VERBOSE)
		#work out the intercept of the start/end line
		intercept = calculateIntercept((startlong, startlat), grad)
		dprint("intercept of transect with 0 longitude is "+str(intercept), VERBOSE)

	#print KML preamble
	printKMLpreamble("Transect ("+str(startlat)+","+str(startlong)+") --> ("+str(endlat)+","+str(endlong)+")")


	result = {}

	#now see which ones are within tol of the line
	for cd in cddat.values():
		#find lat/long of point
		cdlat=cd['lat']
		cdlong=cd['long']
		dprint("checking CD "+str(cd['cdnum'])+" at lat "+str(cdlat)+" long "+str(cdlong), VERBOSE)

		#make sure that the point is internal to the line
		minlat=min(startlat, endlat)
		maxlat=max(startlat, endlat)
		minlong=min(startlong, endlong)
		maxlong=max(startlong, endlong)
		if(cdlat < maxlat+tol and cdlat > minlat-tol and cdlong < maxlong+tol and cdlong > minlong-tol):
			#now find distance between the intersect and the cd centroid
			if(not(constantlongitude)):
				intersect = calculatePerpendicularIntersection((cdlong, cdlat), grad, intercept)
				if(intersect[0] < minlong or intersect[0] > maxlong or intersect[1] < minlat or intersect[1] > maxlat):
					#intersect is external to interval bounded by start and end point
					continue
				dist = calculateDistanceOfPointFromLine((cdlong, cdlat), grad, intercept)
			#for constant longitude, we need this special case to make sure intersection is internal to line
			elif(cdlat >= minlat and cdlat <= maxlat and cdlong >= minlong and cdlong <= maxlong):	
				dist = abs(cdlong-startlong)
			else:
				continue #constant longitude intersect is external to interval, skip it

			if(dist < tol):
				cdnum=cd['cdnum']
				dprint(str(cdnum)+" is along transect (distance "+str(dist)+")", NORMAL)
				result[cdnum] = cd
				printKMLforCD(cdnum, cdlat, cdlong)

	printKMLfooter()
	return result


	



def printKMLpreamble(name):
	#write the KML preamble
	print "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
	print "<kml xmlns=\"http://earth.google.com/kml/2.0\">"
	print "<Document>"
	print "  <description>ABS collection district centroids</description>"
	print "  <name>"+name+"</name>"
	print "  <visibility>0</visibility>"
	print "  <Style id=\"normalPlacemark\">"
	print "    <IconStyle>"
	print "      <Icon>"
	print "        <href>root://icons/palette-4.png</href>"
	print "        <x>128</x>"
	print "        <y>32</y>"
	print "        <w>32</w>"
	print "        <h>32</h>"
	print "      </Icon>"
	print "    </IconStyle>"
	print "  </Style>"

def printKMLfooter():
	#write footer KML tags
	print "</Document>"
	print "</kml>"
	
def printKMLforCD(cd, lat, long):
	#now go through and place the CD's on the map
	print "  <Placemark>"
	print "    <name>CD "+str(cd)+" </name>"
	print "    <styleUrl>#normalPlacemark</styleUrl>"
	print "    <visibility>0</visibility>"
	print "    <Point>"
	print "      <coordinates>"+str(long)+","+str(lat)+"</coordinates>"
	print "    </Point>"
	print "  </Placemark>"

	






#given a string like $300-$499, return the tuple (300,499)
#which gives the upper and lower bounds
def getBoundsOnDollarString(dollarstring):
	if(len(dollarstring.split("-")) != 2):
		return None
	lwr = int(("".join(dollarstring.split("-")[0].replace(",", " ").split()))[1:])
	upr = int(("".join(dollarstring.split("-")[1].replace(",", " ").split()))[1:])
	return (lwr, upr)


def extractInfo(cd_int):
	dprint("extracting information about CD "+str(cd_int), NORMAL)
	datadir=getDataDir(cd_int)
	if(os.path.exists(datadir)): #datadir in os.listdir(".")):
		extractSummaryInfo(cd_int)
		extractIncome(cd_int)
		extractDwellingInfo(cd_int)
		extractRent(cd_int)
		extractDensity(cd_int)
		extractAgeInfo(cd_int)
		extractVisitorInfo(cd_int)
		extractDemographicInfo(cd_int)
		extractHousingRepaymentInfo(cd_int)
		extractEmploymentAndTransienceInfo(cd_int)
		extractWorkTravelInfo(cd_int)
		extractCarOwnershipInfo(cd_int)
		extractHouseholdIncomeInfo(cd_int)
		extractBirthplaceInfo(cd_int)
		#extractAncestryInfo(cd_int) NOT DONE YET





def extractBirthplaceInfo(cd_int):
	datadir=getDataDir(cd_int)
	lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".14").readlines()
	

	index=0
	while(lines[index].find("Australia") < 0):
		index=index+1

	australian = safeint(lines[index].split()[-1]) 
	canada =  safeint(lines[index+1].split()[-1])
	china =  safeint(lines[index+2].split()[-1])
	croatia =  safeint(lines[index+3].split()[-1])
	egypt =  safeint(lines[index+4].split()[-1])
	fiji =  safeint(lines[index+5].split()[-1])
	france =  safeint(lines[index+6].split()[-1])
	germany =  safeint(lines[index+7].split()[-1])
	greece =  safeint(lines[index+8].split()[-1])
	hongkong =  safeint(lines[index+9].split()[-1])
	india =  safeint(lines[index+10].split()[-1])
	indonesia =  safeint(lines[index+11].split()[-1])
	ireland =  safeint(lines[index+12].split()[-1])
	italy =  safeint(lines[index+13].split()[-1])
	korea =  safeint(lines[index+14].split()[-1])
	lebanon =  safeint(lines[index+15].split()[-1])
	macedonia =  safeint(lines[index+16].split()[-1])
	malaysia =  safeint(lines[index+17].split()[-1])
	malta =  safeint(lines[index+18].split()[-1])
	netherlands =  safeint(lines[index+19].split()[-1])
	nz =  safeint(lines[index+20].split()[-1])
	phillipines =  safeint(lines[index+21].split()[-1])
	poland =  safeint(lines[index+22].split()[-1])
	singapore =  safeint(lines[index+23].split()[-1])
	southafrica =  safeint(lines[index+24].split()[-1])
	srilanka =  safeint(lines[index+25].split()[-1])
	turkey =  safeint(lines[index+26].split()[-1])
	UK =  safeint(lines[index+27].split()[-1])
	US =  safeint(lines[index+28].split()[-1])
	vientnam =  safeint(lines[index+29].split()[-1])
	yugoslavia =  safeint(lines[index+30].split()[-1])
 	other = safeint(lines[index+31].split()[-1])
 	notstated = safeint(lines[index+32].split()[-1])
 	visitors = safeint(lines[index+33].split()[-1])
	
	while(lines[index].find("Total") < 0):
		index = index+1
 	total = float(safeint(lines[index].split()[-1]))

	if(total > 0):	
		cddat[cd_int]['pct_australian_born'] = australian/total
		cddat[cd_int]['pct_anglo'] = (australian+canada+ireland+nz+southafrica+US+UK)/total



def extractWorkTravelInfo(cd_int):
	datadir=getDataDir(cd_int)
	lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".40").readlines()
	if(noncompleteRecord(lines)):
		return  #not a full profile, we dont get this info

	totalonemode =safeint(lines[21].split()[-1])
	totaltwomode =safeint(lines[27].split()[-1])
	totalthreemode =safeint(lines[32].split()[-1])

	cardriver = safeint(lines[14].split()[-1]) #car driver
	carpass = safeint(lines[15].split()[-1]) #car passenger
	truck = safeint(lines[16].split()[-1]) #truck
	
	trainonly = safeint(lines[9].split()[-1]) 
	busonly = safeint(lines[10].split()[-1]) 
	ferryonly = safeint(lines[11].split()[-1]) 
	tramonly = safeint(lines[12].split()[-1]) 
	taxionly = safeint(lines[13].split()[-1]) 
	
	bike = safeint(lines[18].split()[-1]) 
	walked = safeint(lines[20].split()[-1]) 

	if(totalonemode > 0):
		#% of one-mode trips made by public transport
		onemode_pubtran_pct = (trainonly+busonly+ferryonly+tramonly)/float(totalonemode)
		#as above, but include walking and bicycling as pt
		onemode_pubtranwalk_pct = (trainonly+busonly+ferryonly+tramonly+bike+walked)/float(totalonemode) 


	trainandbus = safeint(lines[23].split()[-1])
	trainandother = safeint(lines[24].split()[-1])
	busandother = safeint(lines[25].split()[-1])
	trainandother2 = safeint(lines[29].split()[-1])
	busandother2 = safeint(lines[30].split()[-1])

	ptcombo = trainandbus+trainandother+busandother+trainandother2+busandother2

	totaltrain = trainonly+trainandbus + trainandother + trainandother2

	totalworkjourneys = totalonemode+totaltwomode+totalthreemode
	cddat[cd_int]['totalworkjourneys'] = totalworkjourneys


	#work out % of work trips by p.t
	if(totalworkjourneys > 0):
		jtw_publicTransport_pct = (trainonly+ferryonly+busonly+tramonly+ptcombo)/float(totalworkjourneys)
		jtw_train_pct = (totaltrain)/float(totalworkjourneys)

		cddat[cd_int]['jtw_publicTransport_pct'] = jtw_publicTransport_pct
		cddat[cd_int]['jtw_train_pct'] = jtw_train_pct

		cddat[cd_int]['jtw_autoonly_pct'] = (cardriver+carpass+truck)/float(totalworkjourneys)
		cddat[cd_int]['jtw_cardriver_pct'] = (cardriver)/float(totalworkjourneys)
		cddat[cd_int]['jtw_carpassengeronly_pct'] = (carpass)/float(totalworkjourneys)
	
		cddat[cd_int]['jtw_walkorbike_pct'] = (walked+bike)/float(totalworkjourneys)

	workedathome = safeint(lines[33].split()[-1])	
	if(workedathome + totalworkjourneys > 0):
		cddat[cd_int]['workedathome_pct'] = workedathome/float(workedathome+totalworkjourneys)



def extractCarOwnershipInfo(cd_int):
	datadir=getDataDir(cd_int)
	lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".41").readlines()
        if(noncompleteRecord(lines)):
		return  #not a full profile, we dont get this info

	#now work out cars per household and cars per person
	nonecar = safeint(lines[13].split()[-1])
	onecar = safeint(lines[20].split()[-1])
	twocar = safeint(lines[27].split()[-1])
	threeormorecar = safeint(lines[34].split()[-1])
	#estimate that the number of 4 car households is 
	#half the number of 3 car households. Use this
	#assumption to estimate 3 and 4 car households seperately
	#assume no 5 car households
	threecarest=threeormorecar*0.6666667
	fourcarest=threeormorecar*0.3333333	

	totalhouseholds=nonecar+onecar+twocar+threeormorecar

	if(totalhouseholds > 9):
		#count three or more cars as three cars per household
		#this means that there will be a slight underestimate
		#of the number of cars per household
		cddat[cd_int]['carhh'] = totalhouseholds
		cddat[cd_int]['cars_per_household'] = (onecar+2*twocar+3*threecarest+4*fourcarest)/float(totalhouseholds)
		cddat[cd_int]['pct_nocar_households'] = (nonecar)/float(totalhouseholds)

		#now estimate the number of cars per person
		cddat[cd_int]['cars_per_person'] = cddat[cd_int]['cars_per_household']/cddat[cd_int]['people_per_household']


def extractHouseholdIncomeInfo(cd_int):
	datadir=getDataDir(cd_int)
	lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".43").readlines()
	if(noncompleteRecord(lines)):
		return  #not a full profile, we dont get this info
	
	
	hhincome = [safeint(item.split()[-1]) for item in lines[9:22]]
	hhmed = findMedian(hhincome) 
	famincome = [safeint(item.split()[-3]) for item in lines[9:22]]
	fammed = findMedian(famincome) 
	nonfamincome = [safeint(item.split()[-2]) for item in lines[9:22]]
	nonfammed = findMedian(nonfamincome)

	#go through and, if required, make an estimate of the median,
	#if it cannot be calculated directly
	for i in [(hhmed, hhincome, 'household'), (fammed, famincome, 'familyhousehold'), (nonfammed, nonfamincome, 'nonfamilyhousehold')]:
		if(math.ceil(i[0]) == len(i[1])):
			tot = sum(i[1])-i[1][-1]
			if(tot > 9):
				dolpersample = 2000.0/tot
				est = dolpersample*(sum(i[1])/2.0)
				est = (est + 2000.0)/2
				cddat[cd_int]['median_'+i[2]+'_income'] = est
		else:
			bounds = getBoundsOnDollarString(lines[int(math.floor(9+i[0]))].split()[0])
			if(bounds != None):
				lwr = bounds[0]
				upr = bounds[1]
				offset = i[0]-math.floor(i[0])
				cddat[cd_int]['median_'+i[2]+'_income'] = lwr+offset*(upr-lwr)
	 
 



def extractEmploymentAndTransienceInfo(cd_int):
	datadir=getDataDir(cd_int)
	lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".32").readlines()
	if(noncompleteRecord(lines)):
		return  #not a full profile, we dont get this info
	

	ftemployed = safeint(lines[9].split()[-1])
	ptemployed = safeint(lines[10].split()[-1])
	unemployed = safeint(lines[13].split()[-1])
	labourforce = safeint(lines[14].split()[-1])
	notinlabourforce = safeint(lines[15].split()[-1])
	unemplstr = lines[16].split()[-2] 
	unemployment_byabsdefn = float(unemplstr)

	pop = float(cddat[cd_int]['population'])
	cddat[cd_int]['pct_population_parttime_employed'] = ptemployed/pop
	cddat[cd_int]['pct_population_fulltime_employed'] = ftemployed/pop
	cddat[cd_int]['unemployment_rate'] = unemployment_byabsdefn
	if(labourforce > 0):
		cddat[cd_int]['ft_employment_rate'] = ftemployed/float(labourforce)
	
	#estimate underemployment by counting the percentage of 
	#males that are part-time or unemployed, as a percentage of
	#all males in labour force. Done this way because females
	#are more likely to have child-rearing responsibilities,
	#so a more robust estimate is done this way
	ftmale = safeint(lines[9].split()[-3]) 
	ptmale = safeint(lines[10].split()[-3]) 
	unemployedmale = safeint(lines[13].split()[-3])
	malesinlabourforce = safeint(lines[14].split()[-3])
	if(malesinlabourforce > 0):
		underemployed = (ptmale+unemployedmale)/float(malesinlabourforce)
		cddat[cd_int]['underemployment_rate_est'] = underemployed


	#now do the transience info
	sameaddress1yearago = safeint(lines[18].split()[-1])
	differentaddress1yearago = safeint(lines[19].split()[-1])
	sameaddress5yearago = safeint(lines[21].split()[-1])
	differentaddress5yearago = safeint(lines[22].split()[-1])
	
	cddat[cd_int]['1year_pop_turnover'] = differentaddress1yearago/float(sameaddress1yearago+differentaddress1yearago)
	cddat[cd_int]['5year_pop_turnover'] = differentaddress5yearago/float(sameaddress5yearago+differentaddress5yearago)



def extractHousingRepaymentInfo(cd_int):
	datadir=getDataDir(cd_int)
	lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".30").readlines()


	mortrepay = [safeint(item.split()[-1]) for item in lines[8:19]]
	med = findMedian(mortrepay)
	bounds=getBoundsOnDollarString(lines[int(8+math.floor(med))].split()[0])
	if(bounds != None):		
		lwr = bounds[0]
		upr = bounds[1]
		meddollar = lwr+(med-int(math.floor(med)))*(upr-lwr)
		cddat[cd_int]['median_monthly_housing_repayments'] = meddollar
	else: #bounds == None
		#make a rough guess at what the median might be
		if(math.ceil(med) == len(mortrepay)):
			tot = sum(mortrepay)-mortrepay[-1]
			if(tot > 9):
				dolpersample = 2000.0/tot
				est = (sum(mortrepay)/2.0)*dolpersample
				est = (2000+est)/2
				cddat[cd_int]['median_monthly_housing_repayments'] = est
		
	
	numwithmortgage = safeint(lines[21].split()[-1])
	if('households' in cddat[cd_int].keys()):
		numhouseholds = cddat[cd_int]['households']
		if(numhouseholds > 9): 
			cddat[cd_int]['num_households_with_mortgage'] = numwithmortgage
			cddat[cd_int]['pct_households_with_mortgage'] = numwithmortgage/float(numhouseholds)

	#estimate the number of owners with no mortgage
	lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".29").readlines()
	ownerswithoutmortgage = safeint(lines[19].split()[1])
	dwellingscountedinB19 = safeint(lines[19].split()[10])
	if(dwellingscountedinB19 > 9):
		cddat[cd_int]['pct_outright_owners'] = ownerswithoutmortgage/float(dwellingscountedinB19)




def extractDemographicInfo(cd_int):
	dprint("extracting demographic information", NORMAL)
	datadir=getDataDir(cd_int)
	lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".44").readlines()
	if(noncompleteRecord(lines)):
		return  #not a full profile, we dont get this info
	
	two = safeint(matchLine(lines, "Two").split()[1])
	three = safeint(matchLine(lines, "Three").split()[1])
	four = safeint(matchLine(lines, "Four").split()[1])
	five = safeint(matchLine(lines, "Five").split()[1])
	sixormore = safeint(matchLine(lines, "Six or more").split()[3])
	totalfamily = two+three+four+five+sixormore
	totalthreeormorefamily = totalfamily-two
	totalnonfamily = safeint(lines[17].split()[2])+ \
	                 safeint(lines[17].split()[3])
	totallonehousehold = safeint(lines[10].split()[-1])
	totalhouseholds = safeint(lines[17].split()[-1])
	cddat[cd_int]['households'] = totalhouseholds
	if(totalhouseholds == 0): #no households in CD
		return
		#raise Exception("no households in Census District!")

	cddat[cd_int]['pct_lone_households'] = totallonehousehold/float(totalhouseholds)
	cddat[cd_int]['pct_threeplus_households'] = totalthreeormorefamily/float(totalhouseholds)
	cddat[cd_int]['pct_family_households'] = totalfamily/float(totalhouseholds)
	cddat[cd_int]['people_per_household'] = float(cddat[cd_int]['population'])/totalhouseholds


	#now look up other demographic info
	lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".27").readlines()

	loneparentwithoutdependants = safeint(lines[27].split()[-4])
	totalloneparenthouseholds =safeint(lines[28].split()[-4]) 
	loneparentwithdependants = totalloneparenthouseholds-loneparentwithoutdependants

	couplenokids = safeint(lines[18].split()[-4])
	couplekids = safeint(lines[16].split()[-4])
	coupleolderkids = safeint(lines[15].split()[-4])  #non dependant children
	if(totalhouseholds < totalloneparenthouseholds+couplenokids+coupleolderkids):
		totalhouseholds = totalloneparenthouseholds+couplenokids+coupleolderkids
	cddat[cd_int]['pct_couplenokids'] = couplenokids/float(totalhouseholds)
	cddat[cd_int]['pct_couplewithkids'] = couplekids/float(totalhouseholds)
	cddat[cd_int]['pct_couplewithnondependantkids'] = coupleolderkids/float(totalhouseholds)
	cddat[cd_int]['pct_lone_parent_households'] = totalloneparenthouseholds/float(totalhouseholds)
	cddat[cd_int]['pct_lone_parent_households_withdependants'] = loneparentwithdependants/float(totalhouseholds)



#work out the population density of the CD
def extractDensity(cd_int):
	#work out the population of the area
	datadir=getDataDir(cd_int)
	lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".8").readlines()
	tpline = matchLine(lines, "total persons")
	dprint("pop line is "+tpline, VERY_VERBOSE)	
	male = safeint(tpline.split()[-3])
	female = safeint(tpline.split()[-2])
	pop = safeint(tpline.split()[-1])
	if(pop != (male+female)):
		raise Exception("there are unsexed individuals in CD "+str(cd_int))
	
	#get the area of the cd
	area = cddat[cd_int]['area']
	
	#now calculate the population density in people/sqkm
	#area cannot be zero because we exclude CDs with 0 area at the start
	density = pop/area
	cddat[cd_int]['pop_density'] = density
	



#extract basic info such as the number of males and females
def extractSummaryInfo(cd_int):
	datadir=getDataDir(cd_int)
	if(os.path.exists(datadir)): #datadir in os.listdir(".")):
		lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".8").readlines()

		cddat[cd_int]['population'] = safeint(lines[8].split()[-1])
		cddat[cd_int]['males'] = safeint(lines[8].split()[-3])
		cddat[cd_int]['females'] = safeint(lines[8].split()[-2])
		pop =float(cddat[cd_int]['population']) 
		if(pop > 0):
			cddat[cd_int]['pct_male'] = cddat[cd_int]['males']/pop
	


#extract information about the number and type of dwellings
def extractDwellingInfo(cd_int):
	datadir=getDataDir(cd_int)
	lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".28").readlines()

	line = matchLine(lines[26:], "total")
	totaldwellings = float(safeint(line.split()[-2]))
	totalpeople = float(safeint(line.split()[-1]))
	cddat[cd_int]['dwellings'] = int(totaldwellings)
	

	line = matchLine(lines, "separate house")
	houses = safeint(line.split()[-2])
	housepeople = safeint(line.split()[-1])
	cddat[cd_int]['houses'] = houses
	if(totaldwellings > 0):
		cddat[cd_int]['pct_houses'] = houses/totaldwellings
	if(totalpeople > 0):
		cddat[cd_int]['pct_in_houses'] = housepeople/totalpeople


	line = matchLine(lines[12:13], "total")
	semis = safeint(line.split()[-2])
	semipeople = safeint(line.split()[-1])
	cddat[cd_int]['semis'] = semis
	if(totaldwellings > 0):
		cddat[cd_int]['pct_semis'] = semis/totaldwellings
	if(totalpeople > 0):
		cddat[cd_int]['pct_in_semis'] = semipeople/totalpeople


	line = matchLine(lines[18:19], "total")
	flats = safeint(line.split()[-2])
	flatpeople = safeint(line.split()[-1])
	cddat[cd_int]['flats'] = flats
	if(totaldwellings > 0):
		cddat[cd_int]['pct_flats'] = flats/totaldwellings
	if(totalpeople > 0):
		cddat[cd_int]['pct_in_flats'] = flatpeople/totalpeople
	

	#work out how many flats are high-rise (4 or more storey)
	line = matchLine(lines, "in a one or two storey block")
	lowrise = safeint(line.split()[-2])
	lowrisepeople = safeint(line.split()[-1])
	line = matchLine(lines, "in a three storey block")
	lowrise = lowrise + safeint(line.split()[-2])
	lowrisepeople = lowrisepeople + safeint(line.split()[-1])
	
	line = matchLine(lines, "in a four or more")
	highrise = safeint(line.split()[-2])
	highrisepeople = safeint(line.split()[-1])

	if(totalpeople > 0):
		cddat[cd_int]['pct_in_freestanding'] = housepeople/totalpeople
		cddat[cd_int]['pct_in_medium_density'] = (semipeople+lowrisepeople)/totalpeople
		cddat[cd_int]['pct_in_high_density'] = highrisepeople/totalpeople
		cddat[cd_int]['pct_in_medium_or_high_density'] = (lowrisepeople+semipeople+highrisepeople)/totalpeople
	cddat[cd_int]['small_flats'] = lowrise 
	cddat[cd_int]['large_flats'] = highrise
	

	#work out dwelling density
	cddat[cd_int]['dwelling_density'] = totaldwellings/float(cddat[cd_int]['area'])
	

		
#find out how connected different districts are, as measured by the
#chance of having a visitor present on census night		
def extractAgeInfo(cd_int):
	datadir=getDataDir(cd_int)
	lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".10").readlines()

	totalpeople = cddat[cd_int]['population']
	if(totalpeople == 0):
		return

	ageinfo = {}

	ageinfo['pct_0_4'] = safeint(lines[13].split()[3])/float(totalpeople)
	ageinfo['pct_5_9'] = safeint(lines[19].split()[3])/float(totalpeople)
	ageinfo['pct_10_14'] = safeint(lines[25].split()[3])/float(totalpeople)


	#get the rest by looking at age by marital status by sex
	lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".11").readlines()

	minage = 15
	maxage = 19
	lnum = 9
	while(maxage <= 84):
		tot = safeint(lines[lnum].split()[-1])
		ageinfo['pct_'+str(minage)+'_'+str(maxage)] = tot/float(totalpeople)
		males = safeint(lines[lnum].split()[-3])
		ageinfo['pct_'+str(minage)+'_'+str(maxage)+'_males'] = males/float(totalpeople)
		females = safeint(lines[lnum].split()[-2])
		ageinfo['pct_'+str(minage)+'_'+str(maxage)+'_females'] = females/float(totalpeople)
		lnum = lnum + 1
		minage = minage + 5
		maxage = maxage + 5

	tot = safeint(lines[23].split()[-1])
	ageinfo['pct_85plus'] = tot/float(totalpeople)
	tot = safeint(lines[23].split()[-3])
	ageinfo['pct_85plus_males'] = tot/float(totalpeople)
	tot = safeint(lines[23].split()[-2])
	ageinfo['pct_85plus_females'] = tot/float(totalpeople)

	#define working age as 20-65 year olds
	workingage = 0
	for m in range(20,61,5):
		agestring = 'pct_'+str(m)+'_'+str(m+4)
		workingage = workingage+ageinfo[agestring]
	cddat[cd_int]['pct_workingage'] = workingage
 

	#work out:
	#         % children (0-9)
	#         % children altogether (0-19)
	cddat[cd_int]['pct_young_children'] = ageinfo['pct_0_4']+ageinfo['pct_5_9']
	cddat[cd_int]['pct_children'] = cddat[cd_int]['pct_young_children']+ageinfo['pct_10_14']+ageinfo['pct_15_19']
	




def extractVisitorInfo(cd_int):
	datadir=getDataDir(cd_int)
	lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".24").readlines()

	austvisitor = int(lines[54].split()[-1])
	osvisitor = int(lines[55].split()[-1])
	workingageaustvisitor = sum(map(int, lines[54].split()[-8:-3]))
	workingageosvisitor = sum(map(int, lines[55].split()[-8:-3]))
	totalpeople = cddat[cd_int]['population']

	workingage = cddat[cd_int]['pct_workingage']*totalpeople

	if(workingage > 0):
		cddat[cd_int]['workingage_australian_visitors_per_workingage_person'] = workingageaustvisitor/float(workingage)
		cddat[cd_int]['workingage_os_visitors_per_workingage_person'] = workingageosvisitor/float(workingage)
		cddat[cd_int]['workingage_visitors_per_workingage_person'] = (workingageosvisitor+workingageaustvisitor)/float(workingage)

		
	

def extractIncome(cd_int):
	datadir=getDataDir(cd_int)
	lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".23").readlines()

	#skip to the start of the table
	incometablelines=list()
	index=0
	while lines[index].find("PERSONS") < 0:
		index = index+1
	
	index = index+1
	#now read in the table
	while(not(lines[index].lower().startswith("not stated"))):
		line=map(string.strip, lines[index].split())
		if(len(line)> 0):
			incometablelines.append(line)
			#print line
		index = index+1


	def last(l):
		return int(l[-1])
	incomecountlist=map(last, incometablelines)
	med=findMedian(incomecountlist)

	dollarstring=incometablelines[int(med)][0]
	incomebounds=getBoundsOnDollarString(dollarstring)
	if(incomebounds == None and math.ceil(med) == len(incomecountlist)):
		#make an rough guess at median income
		tot = sum(incomecountlist)-incomecountlist[-1]
		if(tot > 9):
			dolpersample = 1500.0/(sum(incomecountlist)-incomecountlist[-1])
			est = (sum(incomecountlist)/2.0)*dolpersample
			est = (est + 1500)/2.0
			cddat[cd_int]['median_income'] = est 
			print "Median_income = "+dollarstring
	elif(incomebounds == None):
		pass
	else:
		lwr = incomebounds[0]
		upr = incomebounds[1]
		offset=med-int(med)
		income =lwr+offset*(upr-lwr) 
		cddat[cd_int]['median_income'] = income 
		print "Median_income = "+str(income)
 
		





def extractRent(cd_int):
	datadir=getDataDir(cd_int)

	#first lets see how many houses there are and how many units
	lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".29").readlines()
	houses=int(lines[13].split()[-6])
	semi=int(lines[14].split()[-6])
	flat=int(lines[15].split()[-6])
	dwellings=houses+semi+flat
	if(dwellings == 0):
		return #no rental dwellings!!
	print "Total_Rental_dwellings = "+str(dwellings)
	print "Num_houses = "+str(houses)
	print "Num_semis = "+str(semi)
	print "Num_flats = "+str(flat)

	alldwellings = float(cddat[cd_int]['dwellings'])
	cddat[cd_int]['pct_rental_dwellings'] = dwellings/alldwellings

	


	lines=open(datadir+os.sep+"BCP_"+str(cd_int)+".31").readlines()
	
	#find the lines that start with a $
	rentlines = [item for item in lines if item.startswith("$")]

	#just get the first column (real-estate agent housing)
	rents = [int(item.split()[-5]) for item in rentlines if item] 	
	med=findMedian(rents)
	flatmed = -1
	housemed = -1
	semimed = -1
	if(flat > 0):
		flatmed=findPosition(rents, (flat/2.0)/dwellings)
	if(houses > 0):
		housemed=findPosition(rents, (flat+semi+houses/2.0)/dwellings)
	if(semimed > 0):
		semimed=findPosition(rents, (flat+semi/2.0)/dwellings)

	printstring = ""
	for pos in [(med, "median_rent"), (flatmed, "median_flatrent"), (semimed, "median_semirent"), (housemed, "median_houserent")]:	 
		if(pos[0] < 0):
			dollarstring="NA"
			rentbounds=None
		else:
			dollarstring=rentlines[int(pos[0])].split()[0]
			rentbounds=getBoundsOnDollarString(dollarstring)
			#make an estimate of median rent if we have to,
			#but dont estimate any other medians, its too iffy
			if(rentbounds == None and math.ceil(pos[0]) == len(rents) and pos[1] == "median_rent"):
				tot = sum(rents)-rents[-1]
				if(tot > 9):
					dolpersample = 500.0/tot
					est = dolpersample*(sum(rents)/2.0)
					est = (est+500.0)/2
					rentbounds = (500.0, est)
				

		if(rentbounds == None):
			printstring = printstring + pos[1]+" = "+dollarstring+"    "
		else:
			lwr = rentbounds[0]
			upr = rentbounds[1]
			offset=pos[0]-int(pos[0])
			rent =lwr+offset*(upr-lwr) 
			printstring = printstring + pos[1]+" = "+str(rent)+"    "
			cddat[cd_int][pos[1]] = rent

	#now make a really rough estimate of the land rent
	#by multiplying rent per dwelling by the dwelling 
	#density
	if(cddat[cd_int].has_key('median_rent')):
		medrent = cddat[cd_int]['median_rent']
		totalrentpaid = medrent*dwellings
		rentpersqkm = totalrentpaid/cddat[cd_int]['area']
		rentpersqm = rentpersqkm/(1000.0**2)
		cddat[cd_int]['rent_persqm_est'] = rentpersqm 

	print printstring



	
	
#if(len(sys.argv) <= 1):
#	print "available functions are:"
#	funcs = locals().copy()
#	for item in funcs.values():
#		if(callable(item)):
#			print item
	
#else:
#	funcname=sys.argv[1]

#	functocall=locals().get(funcname)
#	if(functocall == None):
#		pass #raise Exception("specified utility function does not exist")
#	else:
#		functocall(*(sys.argv[2:]))

