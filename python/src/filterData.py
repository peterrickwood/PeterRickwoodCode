import sys
import math



def filterVals(vals, numstddev):
	tmpvals = vals

	while(1):
		mean = sum(tmpvals)/len(tmpvals)
		variance = sum([(item-mean)**2 for item in tmpvals])/len(tmpvals)
		stddev = math.sqrt(variance)

		newvals = [item for item in tmpvals if abs((item-mean)/stddev) < numstddev]
		if(len(newvals) == len(tmpvals)): # no filtering took place, so we stop
			break
		else: #we continue
			#print "filtered out "+str(len(tmpvals)-len(newvals))+" values, "+str(len(newvals))+" left"
			tmpvals = newvals

	return tmpvals




#given a data file where each line is a vector, this 
#script filters out 'outliers' by looking at a particular column
coltofilteron = int(sys.argv[2])


datalines = open(sys.argv[1]).readlines()
datalines = [line for line in datalines if line[0] != "#"]

vals = []
for line in datalines:
	bits = line.split()
	vals.append(float(bits[coltofilteron]))


#ok, now filter while we are within 3 standard deviations
validvals = filterVals(vals, 3.0)


validvals.sort()
print "#original data set contained "+str(len(vals))+" data points"
print "#filtered data set contained "+str(len(validvals))+" data points"
print "#bounds on values are "+str(validvals[0])+" .. "+str(validvals[-1])
#now print out the data that are not filtered
for line in datalines:
	val = float(line.split()[coltofilteron])
	if(val >= validvals[0] and val <= validvals[-1]):
		print line.strip()
	else:
		print "#"+line.strip()



