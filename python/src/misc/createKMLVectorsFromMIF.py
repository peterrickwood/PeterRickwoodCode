

#create google earth vector file (in kml format)
#from a mapinfo MIF file





import sys
import os
import os.path

def printKMLpreamble(name):
	#write the KML preamble
	print "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
	print "<kml xmlns=\"http://earth.google.com/kml/2.0\">"
	print "<Document>"
	print "  <!-- Begin Style Definitions -->"
	print "  <Style id=\"myDefaultStyles\">"
   	print "    <LineStyle id=\"defaultLineStyle\">"
	print "      <color>ff0000ff</color>"
	print "      <width>3</width>"
	print "    </LineStyle>"
	print "  <PolyStyle id=\"defaultPolyStyle\">"
	print "    <color>7f7faaaa</color>"
	print "    <colorMode>random</colorMode>"
	print "    <fill>0</fill>"
	print "  </PolyStyle>"
	print "  </Style>"
	print "  <description>None</description>"
	print "  <name>"+name+"</name>"
	print "  <visibility>0</visibility>"


def printKMLfooter():
	#write footer KML tags
	print "</Document>"
	print "</kml>"


	
def printKMLforPolygon(id, latlongs):
	#now go through and place the CD's on the map
	print "  <Placemark>"
	print "    <name> "+str(id)+" </name>"
	print "    <styleUrl>#myDefaultStyles</styleUrl>"
	print "    <Polygon>"
	print "    <altitudeMode>relativeToGround</altitudeMode>"
	print "      <outerBoundaryIs>"
	print "        <LinearRing>"
	print "          <coordinates>"
	for item in latlongs:
		print str(item[1])+","+str(item[0])+",0"
	print "          </coordinates>"
	print "        </LinearRing>"
	print "      </outerBoundaryIs>"
	print "    </Polygon>"
	print "  </Placemark>"





def skipTo(lines, startindex, identifier):
	index = startindex

	while(1):
		if(index >= len(lines)):
			return -1
		if(lines[index].lower().find(identifier.lower()) >= 0):
			return index
		index = index + 1
	

def filterVals(latlonvals):
	includeindicies=[0]
	base = 0
	skip = 2
	while(base+skip < len(latlonvals)):
		mfact = (skip-1.0)/skip
		predlat = latlonvals[base][0]+(latlonvals[base+skip][0]-latlonvals[base][0])*mfact
		predlon = latlonvals[base][1]+(latlonvals[base+skip][1]-latlonvals[base][1])*mfact
		pred = (predlat, predlon)
		err = (predlat-latlonvals[base+skip-1][0])**2 + (predlon-latlonvals[base+skip-1][1])**2
		if(err > 2):
			includeindicies.append(base+skip-1)
			base = base+skip-1
			skip = 2
		else:
			skip = skip + 1
	newlist = [latlonvals[item] for item in includeindicies]
	if(newlist[0] != newlist[-1]):
		newlist.append(newlist[0])
	#print "filtered from "+str(len(latlonvals))+" to "+str(len(newlist))
	return newlist

#print out KML for a bunch of polygons



lines=open(sys.argv[1]).readlines()
midfile=sys.argv[1].split(".")[0]+".MID"
if(not(os.path.exists(midfile))):
	midfile=midfile.split(".")[0]+".mid"
midlines=open(midfile).readlines()
names=[item.split(",")[1] for item in midlines]


printKMLpreamble("None")

index = skipTo(lines, 0, "Data")
id=0
while(1):
	index = skipTo(lines, index, "Region")
	if(index == -1):
		break #nothing left to do
	npolys = 1
	if len(lines[index].split()) > 1:
		npolys = int(lines[index].split()[1])

	index = index+1
	for i in range(0, npolys):
		nentries = int(lines[index].strip())
		index = index + 1
		latlonvals=[]
		for i in range(0, nentries):
			lat = float(lines[index+i].split()[1])
			#lat = (lat - 6000000.0)/(300000)-33
			lon = float(lines[index+i].split()[0])
			#lon = (lon - 220000)/300000+150
			latlonvals.append((lat, lon))
		#now filter out entries if we can do so without becoming innacurate
		if(latlonvals != []):
			newlatlonvals = filterVals(latlonvals)
			printKMLforPolygon(names[id], latlonvals)
		
		index = index+nentries

	id=id+1


	
printKMLfooter()



