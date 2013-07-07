#given a data file with x,y values (specified in args[0]), it groups the data into
#x-bins of width arg[1], and plots the median and upper and lower
#quartiles for the y values in each bin

import sys

lines = open(sys.argv[1]).readlines()
binsize = int(sys.argv[2])
data = [(float(item.split()[0]), float(item.split()[1])) for item in lines]

#for item in data:
#	print item

minx = min([item[0] for item in data])
maxx = max([item[0] for item in data])
bins = int((maxx-minx)/binsize + 1)
#print "there are "+str(bins)+" bins"

for i in xrange(0, bins):
	lwrx = minx+i*binsize
	uprx = minx+(i+1)*binsize
	vals = [item[1] for item in data if item[0] >= lwrx and item[0] < uprx]
	#print str(lwrx)+" "+str(uprx)+" has "+str(len(vals))
	vals.sort()
	if(len(vals) > 0):
		print str((lwrx+uprx)/2)+" "+str(vals[len(vals)/2])










