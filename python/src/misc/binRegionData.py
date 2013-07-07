import sys
import math
import random

#given a file where each line is of the form
#REGION_ID   value
#
#bin the values into discrete categories.
#
#
#arg1 gives the data file
#arg2 gives the number of categories
#arg3 gives the way in which the binning is done, one of
#	a) equalnumbers -- equal number of regions in each category
#	b) equalranges -- the entire range in partitioned into equal parts
#	c) kmeans -- perform a standard k means cluster
#	d) kmeans_afterlogtrans -- logtransform the values and kmeans trandform that


if(len(sys.argv) != 4):
	print "arg1 -- data file"
	print "arg2 -- number of categories"
	print "arg3 -- way in which binning should be done (equalnumbers, equalranges, kmeans, kmeans_afterlogtrans)"

def compareTupleOnSecond(tupa, tupb):
	if(tupa[1] > tupb[1]):
		return 1
	elif(tupa[1] < tupb[1]):
		return -1
	return 0



def kmeans(data, centroids):
	membership = [0]*len(data)
	ndata = len(data)
	ncentroids = len(centroids)
	counts = [0]*ncentroids
	#print "centroids are "+(" ".join(map(str, centroids)))
	centroids.sort()

	for iter in xrange(0, 20):
		#calculate membership
		for i in xrange(0, ndata):
			mindist = 999999999999.9
			minc = -1	
			for c in xrange(0, ncentroids):
				dist = (data[i][1]-centroids[c])**2
				if(dist < mindist):
					mindist = dist
					minc = c
			membership[i] = minc

		#recalculate centroids
		for c in xrange(0, ncentroids):
			centroids[c] = 0.0
			counts[c] = 0
		for i in xrange(0, ndata):
			c = membership[i]
			centroids[c] = centroids[c]+data[i][1]
			counts[c] = counts[c]+1
		for c in xrange(0, ncentroids):
			if(counts[c] > 0):
				centroids[c] = centroids[c]/counts[c]
			else: #pick random data point as centroid. This can mean centroids are out of order
				centroids[c] = data[random.randint(0, ndata-1)][1]
		centroids.sort()

		#print "centroids are "+(" ".join(map(str, centroids)))
		

	#double check to make sure centroids are all in ascending order
	#print "centroids are "+(" ".join(map(str, centroids)))

	#now write over data values with centroid numbers
	for i in xrange(0, ndata):
		data[i][1] = membership[i]

	

data = [[float(item.split()[0]), float(item.split()[1])] for item in open(sys.argv[1]).readlines()]
nbins = int(sys.argv[2])

if(sys.argv[3] == "equalnumbers"):
	data.sort(compareTupleOnSecond)  #sort on second element
	countperbin = int(round(len(data)/nbins))
	for tag in xrange(0, nbins-1):
		for i in xrange(tag*countperbin, (tag+1)*countperbin):
			data[i][1] = tag
	for i in xrange((nbins-1)*countperbin, len(data)):
		data[i][1] = nbins-1
elif(sys.argv[3] == "equalranges"):
	data.sort(compareTupleOnSecond)
	start = min(data[0][1], data[-1][1])
	binwidth = abs(data[-1][1]-data[0][1])/float(nbins)
	for i in xrange(0, len(data)):
		binnum = int((data[i][1]-start)/binwidth)
		if(binnum == nbins):
			binnum = nbins-1
		data[i][1] = binnum
elif(sys.argv[3] == "kmeans"):
	data.sort(compareTupleOnSecond)
	countperbin = int(round(len(data)/nbins))
	centroids = []
	for i in xrange(countperbin/2, len(data), countperbin):
		centroids.append(data[i][1])
	kmeans(data, centroids)
elif(sys.argv[3] == "kmeans_afterlogtrans"):
	data.sort(compareTupleOnSecond)
	for i in xrange(0, len(data)):
		newval = math.log(data[i][1]+1)
		#print str(data[i][1])+" "+str(newval)
		data[i][1] = newval

	countperbin = int(round(len(data)/nbins))
	centroids = []
	for i in xrange(countperbin/2, len(data), countperbin):
		centroids.append(data[i][1])
	kmeans(data, centroids)
else:
	raise Exception("Dont know how to do this")



for i in xrange(0, len(data)):
	print str(int(data[i][0]))+" "+str(int(data[i][1]))
	
	






 


