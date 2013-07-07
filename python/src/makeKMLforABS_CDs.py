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
		#only include CD's around the Sydney Metro Region.
		#May get some of Newcastle/Canberra/Woolongong as well, but thats OK
		if(lat > -34.5 and lat < -33 and long > 150 and long < 151.75):
			datlist = list()
			datlist.append(cdnum)
			datlist.append(area_sqkm)
			datlist.append(lat)
			datlist.append(long)
			cdata.append(datlist)
	

#write the KML preamble
print "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
print "<kml xmlns=\"http://earth.google.com/kml/2.0\">"
print "<Document>"
print "  <description>ABS collection district centroids</description>"
print "  <name>ABS CD centroids</name>"
print "  <visibility>0</visibility>"
#now go through and place all the ABS CD's on the map
for datum in cdata:
	lat = datum[2]
	long = datum[3]
	print "  <Placemark>"
	print "    <name> CD "+str(datum[0])+" </name>"
	print "    <visibility>0</visibility>"
	print "    <Point>"
	print "      <coordinates>"+str(long)+","+str(lat)+"</coordinates>"
	print "    </Point>"
	print "  </Placemark>"
#write footer KML tags
print "</Document>"
print "</kml>"

