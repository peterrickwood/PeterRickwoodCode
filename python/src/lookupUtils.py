import sys


def mapCDsToPostcodes(cds):
	"""
	Given a list of Collector District IDs, return a list
	of the same length that gives the postcode for each collector
	district.
	"""
	lines = open("/home/peterr/data/abs_stats/cdinfo.dat").readlines()
	cdmap = {}
	for line in lines:
		cd = int(line.split()[0])
		postcode = int(line.split()[3])
		cdmap[cd] = postcode

	res=[]
	for cd in cds:
		res.append(cdmap[cd])
	return res


def getPostcodeMaps():
	""" 
	Returns a tuple with the first element a suburb to postcode map,
	and the second element a postcode to suburb map.

	The keys (and values) of both of these maps are strings. i.e.
	postcodes are not integers.

	All suburbs are in lower case
	"""
	lines=open('/home/peterr/data/postcodes/pc-full_20070111.csv').readlines()[1:]
	postcodetosuburbmap=[]
	suburbtopostcodemap=[]
	postcodetosuburbmap={}
	suburbtopostcodemap={}
	for line in lines:
		bits = line.split(',')
		postcode = bits[0][1:-1]
		suburb = "_".join(bits[1][1:-1].split())
		suburb = suburb.lower()
		if(postcodetosuburbmap.has_key(postcode)):
			currentval = postcodetosuburbmap[postcode]
			currentval.append(suburb)
			postcodetosuburbmap[postcode] = currentval
		else:
			postcodetosuburbmap[postcode] = [suburb]

		if(suburbtopostcodemap.has_key(suburb)):
			currentval = suburbtopostcodemap[suburb]
			currentval.append(postcode)
			suburbtopostcodemap[suburb] = currentval
		else:
			suburbtopostcodemap[suburb] = [postcode]

	return (suburbtopostcodemap, postcodetosuburbmap)

