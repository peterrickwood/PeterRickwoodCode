import datetime
import sys
import CDutils
import os
import os.path

def printKMLpreamble(name):
	#write the KML preamble
	print "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
	print "<kml xmlns=\"http://www.opengis.net/kml/2.2\">"
	#used to be http://earth.google.com/kml/2.0
	print "<Document>"
	print "  <!-- Begin Style Definitions -->"
	print "  <Style id=\"myDefaultStyles\">"
   	print "    <LineStyle id=\"defaultLineStyle\">"
	print "      <color>ff0000ff</color>"
	print "      <width>3</width>"
	print "    </LineStyle>"
 	print "    <PolyStyle id=\"defaultPolyStyle\">"
	print "      <color>7f7faaaa</color>"
	print "      <colorMode>random</colorMode>"
	print "      <fill>0</fill>"
	print "    </PolyStyle>"
	print "  </Style>"
	print "  <Style id=\"redlinestyle\">"
  	print "    <LineStyle id=\"redlinestyle1\">"
	print "      <color>ff0000ff</color>"
	print "      <width>2</width>"
	print "    </LineStyle>"
	print "  </Style>"
	print "  <Style id=\"greenlinestyle\">"
  	print "    <LineStyle id=\"greenlinestyle1\">"
	print "      <color>ff00ff00</color>"
	print "      <width>2</width>"
	print "    </LineStyle>"
	print "  </Style>"
	print "  <Style id=\"bluelinestyle\">"
  	print "    <LineStyle id=\"bluelinestyle1\">"
	print "      <color>ffff0000</color>"
	print "      <width>2</width>"
	print "    </LineStyle>"
	print "  </Style>"
	print "  <description>None</description>"
	print "  <name>"+name+"</name>"
	print "  <visibility>0</visibility>"





def printKMLfooter():
	#write footer KML tags
	print "</Document>"
	print "</kml>"


def printKMLForLine(latlongs, linecolour):
	print "<Placemark>"
	print "  <name>NONE</name>"
	print "  <description>NONE</description>"
	print "  <styleUrl>#"+linecolour+"linestyle</styleUrl>"
	print "  <LineString>"
	print "  <extrude>0</extrude>"
        print "  <tessellate>0</tessellate>"
	print "  <altitudeMode>absolute</altitudeMode>"
	print "  <coordinates>"
	for latlon in latlongs:
		print "  "+str(latlon[1])+","+str(latlon[0])+",0"
	print "  </coordinates>"
	print "  </LineString>"
	print "</Placemark>"

	
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
		err = CDutils.greatCircDist(pred[0], pred[1], latlonvals[base+skip-1][0], latlonvals[base+skip-1][1])
		if(err > 10):
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


def printPath(listofdatetimelatlonvals, offsetstr=None):
    print "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
    print "<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:gx=\"http://www.google.com/kml/ext/2.2\">"
    print "<Folder>"
    print "  <Placemark>"
    print "    <gx:Track>"
    if offsetstr == None:
        offsetstr="Z"


    #sort the list by date/time
    listofdatetimelatlonvals.sort(lambda (t,la,lo),(t2,la2,lo2): -1 if t < t2 else 1)


    for (time, lat, lon) in listofdatetimelatlonvals:
        time1 = str(time).split()[0]+"T"
        time2 = str(time).split()[1]
        print "        <when>"+time1+time2+offsetstr+"</when>"
    for (time, lat, lon) in listofdatetimelatlonvals:
        print "        <gx:coord>"+str(lon)+" "+str(lat)+" 0.0</gx:coord>"
    print "    </gx:Track>"
    print "  </Placemark>"
    print "</Folder>"
    print "</kml>"



def printPathFromFile(filewithlatlontime, offsetstr=None):
    l = []
    for line in open(filewithlatlontime):
        lat = float(line.split()[2])
        lon = float(line.split()[3])
        t = datetime.datetime.strptime(" ".join(line.split()[0:2]), "%Y-%m-%d %H:%M:%S")
        l.append((t, lat, lon))
    printPath(l, offsetstr)


