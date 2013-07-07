import sys

def printKMLpreamble(name):
	#write the KML preamble
	print "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
	print "<kml xmlns=\"http://earth.google.com/kml/2.0\">"
	print "<Document>"
	print "  <description>None</description>"
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


	
def printKMLforPoint(id, lat, long):
	#now go through and place the CD's on the map
	print "  <Placemark>"
	print "    <name> "+str(id)+" </name>"
	print "    <styleUrl>#normalPlacemark</styleUrl>"
	print "    <visibility>0</visibility>"
	print "    <Point>"
	print "      <coordinates>"+str(long)+","+str(lat)+"</coordinates>"
	print "    </Point>"
	print "  </Placemark>"

	


#print out KML for a bunch of points

def usage():
	print "REM: usage is"
	print ""
	print "arg1: name of file with ID lon/lat"
	print ""
	print "arg2:  indicates ordering, and should be latlon or lonlat"
	print ""
	sys.exit(1)



if(len(sys.argv) != 3):
	usage()

lines=open(sys.argv[1]).readlines()
latindex=2
lonindex=1
if(sys.argv[2] == 'latlon'):
	latindex = 1
	lonindex = 2


printKMLpreamble("None")

for line in lines:
	id=line.split()[0]
	lat=float(line.split()[latindex])
	lon=float(line.split()[lonindex])
	printKMLforPoint(id, lat, lon)

printKMLfooter()











