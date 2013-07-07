#a bunch of utilities to allow for easier converting of data from
#one region (CD, say) to another (TZ, say)




def convertCDtoTZ(cddatafile, average=1):
	""" Given a datafile where each line is of the form [CD , value]
	convert that into data in the format [TZ, value] where the TZ
	value is just either the average or the sum of the CD values
	"""
	cdtotz = getCDtoTZmap()
	tzvals = {}
	tzcounts = {}
	
	cdlines=open(cddatafile).readlines()
	for line in cdlines:
		cd = int(line.split()[0])
		val = float(line.split()[1])
		if not(cd in cdtotz.keys()):
			continue
		tztups = cdtotz[cd]
		for tztup in tztups:
			tz = tztup[0]
			propcdinthattz = tztup[1]
			if not(tz in tzvals.keys()):
				tzvals[tz] = 0.0
				tzcounts[tz] = 0.0
			tzvals[tz] = tzvals[tz] + propcdinthattz*val
			tzcounts[tz] = tzcounts[tz] + propcdinthattz

	
	#now print out all the TZ level data
	for tz in tzvals.keys():
		if(average):
			print str(tz)+" "+str(tzvals[tz]/tzcounts[tz]) 
		else:
			print str(tz)+" "+str(tzvals[tz])
			





def getCDtoTZmap():
	return getMap("/home/peterr/data/tdc/CD_to_tz.dat")



#return a mapping that gives all the regions on type 2 that are wholly or partly
#contained in each region of type 1.
#
#returns a map that maps region ids of type 1 to lists of tuples, where
#each item in the list is a tuple of (regiontype2, proportion)
def getMap(membershipfile):
	f =open(membershipfile)
	lines = f.readlines()
	f.close()
	lines = [line for line in lines if line[0] != "#"]
	
	res = {}
	#REGIONTYPE1 , REGIONTYPE2 : propinregion1 , REGIONTYPE2 : propinregion1 , ...
	#
	#
	for line in lines:
		bits = line.split(",")
		cd = int(bits[0])

		if(not(cd in res.keys())):
			res[cd] = []

		for bit in bits[1:]:
			tz = int(bit.split(":")[0])
			weight = float(bit.split(":")[1])
			res[cd].append([tz, weight])
		
	
	return res



