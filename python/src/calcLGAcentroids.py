
import CDutils


def filterVals(latlonvals):
        includeindicies=[0]
        base = 0
        skip = 2
        while(base+skip < len(latlonvals)):
                pred = ((latlonvals[base][0]+latlonvals[base+skip][0])/2, (latlonvals[base][1]+latlonvals[base+skip][1])/2)
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
        return newlist



def skipTo(startindex, lines, identstring):
	index = startindex
	while(index < len(lines)):
		if(lines[index].find(identstring) >= 0):
			return index
		index = index + 1



def calcArea(vertices):
	fvert=vertices#filterVals(vertices)
	tot=0.0
	for i in range(0, len(fvert)-1):
		tot = tot + fvert[i][0]*fvert[i+1][1]-fvert[i+1][0]*fvert[i][1]
	return tot/2.0

def calcCentroid(vertices, area):
	fvert=vertices#filterVals(vertices)
	cx = 0.0
	cy = 0.0
	for i in range(0, len(fvert)-1):
		xi = fvert[i][0]
		yi = fvert[i][1]
		xip1 = fvert[i+1][0]
		yip1 = fvert[i+1][1]
		cx = cx + (xi+xip1)*(xi*yip1-xip1*yi)
		cy = cy + (yi+yip1)*(xi*yip1-xip1*yi)
	fact = 1/(6*area)
	return (fact*cx, fact*cy)
 


#get the list of LGAs that appear in the MID file
NSWlgalines=open('SYDlga01.MID').readlines()
lgas=[]
for line in NSWlgalines:
	lganum = line.split(',')[0]
	lganame = line.split(',')[1]
	lgas.append((lganame, lganum))


#now go through the MIF file and calculate the centroid of 
#each lga
miflines=open('SYDlga01.MIF').readlines()
index = skipTo(0, miflines, 'Data')
for lga in lgas:
	#skip to where we get the 'Region' string
	index = skipTo(index, miflines, 'Region')+1
	ndat = int(miflines[index].strip())
	index = index+1
	vertices = []
	for i in range(0, ndat):
		lon = float(miflines[index+i].split()[0])
		lat = float(miflines[index+i].split()[1])
		vertices.append((lat, lon))
	
	area = calcArea(vertices)
	centroid = calcCentroid(vertices, area)
	#print "_".join((lga[0][1:-1]).split())+' '+str(centroid[1])+' '+str(centroid[0])
	print lga[1]+" "+"_".join((lga[0][1:-1]).split())+' '+str(centroid[1])+' '+str(centroid[0])
	
	

