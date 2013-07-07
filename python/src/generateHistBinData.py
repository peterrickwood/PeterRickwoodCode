import sys

#
#
#from a datafile with just values listed, bin the data according to the specified # bins
#and print the results 


if(len(sys.argv) < 3):
	print "usage is "
	print "arg1 = name of data file"
	print "arg2 = number of bins"
	print ""
	print "The data file is just a file of single values"



datalines=open(sys.argv[1]).readlines()
nbins=int(sys.argv[2])

data=[]
for line in datalines:
	data.append(float(line.strip()))

data.sort()
minval = data[0]
maxval = data[-1]

#now bin it
count = [0]*nbins

binsize = (maxval-minval)/float(nbins)

for datum in data:
	binnum = int((datum-minval)/binsize)
	if(binnum == nbins):
		binnum = binnum -1
	count[binnum] = count[binnum]+1

for i in range(0, nbins):
	#print str(i)+" "+str(count[i])
	print str(minval+(i*binsize))+" "+str(count[i])


