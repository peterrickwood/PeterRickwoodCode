import numpy
import math


def fitCurveMain():
	datfile = raw_input("name of data file with x,y tuples: ")
	curvetype = raw_input("enter curve type: ")
	datalines = [item for item in open(datfile).readlines() if item[0] != "#"]
	tups = [(float(item.split()[0]), float(item.split()[1])) for item in datalines]
	resstr = fitCurve(tups, curvetype)
	print resstr


def fitCurve(listofxytuples, curvetype):
	ndata = len(listofxytuples)
	xvals = [item[0] for item in listofxytuples]
	yvals = [item[1] for item in listofxytuples]

	if(curvetype == "linear"):
	        A = numpy.zeros((ndata, 2), float)
	        b = numpy.transpose(numpy.matrix(yvals))
	        for i in range(0, ndata):
	                A[i][0] = xvals[i]
	                A[i][1] = 1.0
	        #find least squares solution
	        (x,resids,rank,s) = numpy.linalg.lstsq(A, b, rcond=1.e-10)

		#get predicted values
		predvals = []
		for i in range(0, len(xvals)):
			predvals.append(x[0,0]*xvals[i]+x[1,0])
		rsq = calcRsquared(yvals, predvals)
		print "r-squared for model is "+str(rsq)		

		#return gradient and intercept of that line
		return str(x[0,0])+"*x + "+str(x[1,0])

	elif(curvetype == "poly2"): #degree 2 poly
		A = numpy.zeros((ndata, 3), float)
		b = numpy.transpose(numpy.matrix(yvals))
	        for i in range(0, ndata):
	                A[i][0] = xvals[i]*xvals[i]
	                A[i][1] = xvals[i]
			A[i][2] = 1.0
	        #find least squares solution
	        (x,resids,rank,s) = numpy.linalg.lstsq(A, b, rcond=1.e-10)
		#return gradient and intercept of that line
		return str(x[0,0])+"*x^2 + "+str(x[1,0])+"*x + "+str(x[2,0])
		
	elif(curvetype == "poly3"): #degree three polynomial
		A = numpy.zeros((ndata, 4), float)
		b = numpy.transpose(numpy.matrix(yvals))
	        for i in range(0, ndata):
	                A[i][0] = xvals[i]*xvals[i]*xvals[i]
	                A[i][1] = xvals[i]*xvals[i]
			A[i][2] = xvals[i]
			A[i][3] = 1.0
	        #find least squares solution
	        (x,resids,rank,s) = numpy.linalg.lstsq(A, b, rcond=1.e-10)
		#return gradient and intercept of that line
		return str(x[0,0])+"*x^3 + "+str(x[1,0])+"*x^2 + "+str(x[2,0])+"*x + "+str(x[3,0])
	elif(curvetype == "log"): #logarithmic
		A = numpy.zeros((ndata, 2), float)
		b = numpy.transpose(numpy.matrix(yvals))
	        for i in range(0, ndata):
	                A[i][0] = math.log(xvals[i]+1)
			A[i][1] = 1.0
	        #find least squares solution
	        (x,resids,rank,s) = numpy.linalg.lstsq(A, b, rcond=1.e-10)
		#return gradient and intercept of that line
		return str(x[0,0])+"*log(x+1) + "+str(x[1,0])
	elif(curvetype == "exp"): #exponential
		A = numpy.zeros((ndata, 2), float)
		logyvals=[math.log(item+1) for item in yvals]
		b = numpy.transpose(numpy.matrix(logyvals))
	        for i in range(0, ndata):
	                A[i][0] = xvals[i]
			A[i][1] = 1.0
	        #find least squares solution
	        (x,resids,rank,s) = numpy.linalg.lstsq(A, b, rcond=1.e-10)
		#return gradient and intercept of that line
		#the -1 is there because we are actually solving for
		#(y+1), so to get y we subtract 1 again
		return "exp("+str(x[0,0])+"*x + "+str(x[1,0])+") - 1"
	elif(curvetype == "pow"): #power law
		A = numpy.zeros((ndata, 2), float)
		logyvals=[math.log(item+1) for item in yvals]
		b = numpy.transpose(numpy.matrix(logyvals))
	        for i in range(0, ndata):
	                A[i][0] = math.log(xvals[i]+1)
			A[i][1] = 1.0
	        #find least squares solution
	        (x,resids,rank,s) = numpy.linalg.lstsq(A, b, rcond=1.e-10)
		#return gradient and intercept of that line
		#the -1 is there because we are actually solving for
		#(y+1), so to get y we subtract 1 again
		return "exp("+str(x[0,0])+"*log(x+1) +"+str(x[1,0])+") - 1"
	else:
		raise Exception("unknown curve type "+curvetype)






def calcRsquared(actualvals, listofpredictedvalues):
	meanval = sum(actualvals)/len(actualvals)
	
	sserr = 0.0
	sstot = 0.0
	for i in range(0, len(actualvals)):
		pred = listofpredictedvalues[i]
		actual = actualvals[i]
		sstot = sstot + (actual-meanval)**2
		sserr = sserr + (actual-pred)**2

	rsq = 1-sserr/sstot
	return rsq


















