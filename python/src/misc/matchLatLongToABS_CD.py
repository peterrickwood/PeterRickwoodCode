cdatfile=open("Census2001R1_geog_desc.txt")
clines=cdatfile.readlines()


cdata = list()
for line in clines:
	if(line.split()[0] == "CD"):
		ldat = line.split()
		if(ldat[1] != ldat[2]):
			raise Exception("Inconsistent Census label for CD "+ldat[1]+" / "+ldat[2])
		cdnum = int(ldat[1])
		area_sqkm = float(ldat[3])
		long = float(ldat[4])
		lat = float(ldat[5])
		datlist = list()
		datlist.append(cdnum)
		datlist.append(area_sqkm)
		datlist.append(lat)
		datlist.append(long)
		cdata.append(datlist)
	


def findclosest(lat, long):
#find which census district the specified lat/long are contained in
	bestcd=-1
	bestdist=99999999
	for datline in cdata:
		cdlat=datline[2]
		cdlong=datline[3]
		dist=(cdlat-lat)**2+(cdlong-long)**2
		if(dist < bestdist):
			bestcd=datline[0]
			bestdist=dist
	return bestcd
		






	
		
