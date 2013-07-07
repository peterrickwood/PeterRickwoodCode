
#create data at the TZ level, aggregated up from the CD level


#to do this, we first need to work out the proportion of each CD that
#is in each TZ. Luckily, I've already pre-computed this, so just read it in.
lines=[item for item in open('/home/peterr/data/tdc/CD_to_tz.dat').readlines() if item[0] != "#"]


#a map that maps each tz to a list of tuples, where each tuple
#is a cdnum and the proportion of that cd that lies within the travelzone
tzmap = {}


#rem: each line is CDNUM , TZNUM : PROP , TZNUM : PROP , ...
for line in lines:
	bits = line.split(",")
	cd = int(bits[0])
	tztups = [(int(item.split(":")[0]), float(item.split(":")[1])) for item in bits[1:]]
	for tup in tztups:
		tz = tup[0]
		prop = tup[1]
		if(tz in tzmap.keys()):
			cdlist = tzmap[tz]
			cdlist.append((cd, prop))
		else:
			tzmap[tz] = [(cd, prop)]


#now print out the results
for key in tzmap.keys():
	printstr = str(key)
	vals = tzmap[key]
	for val in vals:
		printstr = printstr + " , " + str(val[0]) + " : "+str(val[1])
	print printstr








