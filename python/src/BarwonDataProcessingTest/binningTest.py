import datetime
import calendar

#Up the top here we put the definitions of the functions that we
#will need in the main code


def getNextMonth(date):
	month = date.month+1
	year = date.year
	if(month == 13):
		month = 1
		year = year + 1
	return datetime.datetime(year, month, 1)


def getMonthIndex(date1, startdate):
	if date1.year==startdate.year and date1.month==startdate.month:
		return 0
	nextmonth = getNextMonth(startdate)
	return getMonthIndex(date1, nextmonth)+1


#get the number of months between these two (inclusive)
def getNumMonthsBetween(startdate, enddate):
	count = 0
	datetmp = startdate
	while(datetmp <= enddate):
		datetmp = getNextMonth(datetmp)
		count = count + 1
	return count

def getNumDaysLeftInMonth(date):
	totaldays=calendar.monthrange(date.year,date.month)
	return totaldays[1]-date.day+1

def getDateTimeObjectFromString(datestr):
	#print datestr
	if(len(datestr.split()) == 0):
		return None
	
	bits = datestr.split("-")
	day = int(bits[0])
	month = [None,"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"].index(bits[1])
	year = int("20"+bits[2])
	return datetime.datetime(year, month, day)


def getElement(nameofelementyouwant, dataline, headerbits):
	indexofthingyouwant = headerbits.index(nameofelementyouwant)
	return dataline.split(",")[indexofthingyouwant].strip()
	



#OK, program itself starts here

#open up data file
datalines = open("combined_0809.csv").readlines()


#look at the structure of the file
headerline = datalines[0]
headerbits = [item.strip() for item in headerline.split(",")]
datalines = datalines[1:]


#get a unique list (actually a dictionary) of installation numbers 
#and map that to a list of data records for that installation number
#each element in the list is a dictionary of key->value mappings
#that correspond to the original data-file structure.
#
#For example  instnums['123456'] will give you a list [a,b,c,d],
#where each item in [a,b,c,d] is itself a dictionary with the
#data for a particular billing record. So:
#instnums['123456'][0]["strdt"] will give you the start date for 
#the first bill for installation '123456'
# 
instnums = {}
for line in datalines:
	instnum = getElement("inst", line, headerbits)
	if not instnums.has_key(instnum):
		instnums[instnum] = []
		print "Got new inst num "+instnum

	bits = [item.strip() for item in line.split(",")]
	if(len(bits) != len(headerbits)):
		raise Exception("header line and data line do not match")

	datadict = {}
	for i in range(0, len(headerbits)):
		datadict[headerbits[i]] = bits[i]
	instnums[instnum].append(datadict)
	#print datadict





#go through each installation and ....
for instnum in instnums.keys():
	#get the data for that installation
	#this is just a group of data lines from the text file
	dataforinst = instnums[instnum]
	startdates=[]
	enddates=[]
	isvalid = True
	for entry in dataforinst:
		startdate=getDateTimeObjectFromString(entry["strdt"])
		enddate=getDateTimeObjectFromString(entry["enddt"])
		if(startdate == None or enddate == None):
			isvalid = False
			break
		startdates.append(startdate)
		enddates.append(enddate)
		
	if not isvalid:
		continue
		
	

	#Get the first start date    
	startdates.sort()
	startdate = startdates[0]
	enddates.sort()
	enddate=enddates[-1]
	
	#CAlculate number of months from start to end
	nummonthsfromstarttoend = getNumMonthsBetween(startdate, enddate)
	
	
	monthlyconsumption = []
	for i in range(0, nummonthsfromstarttoend):
		monthlyconsumption.append([0.0,0.0,False])  #total consumption in months and days of usage billed in month
	
	for entry in dataforinst:
		print "processing bill for "+instnum
		print entry
		rdays=float(entry["rdays"])
		wcons=float(entry["wcons"])
		dailycons=wcons/rdays
		date1=getDateTimeObjectFromString(entry["strdt"])
		date2=getDateTimeObjectFromString(entry["enddt"])
		while date1.month < date2.month or date1.year < date2.year:
			numdaysinmonth = getNumDaysLeftInMonth(date1)
			consumptionthismonth=dailycons*numdaysinmonth
			index = getMonthIndex(date1, startdate)
			print "index for "+str(date1)+" is worked out as "+str(index)+" and num billing days is "+str(numdaysinmonth)
			monthlyconsumption[index][0] = monthlyconsumption[index][0]+consumptionthismonth
			monthlyconsumption[index][1] = monthlyconsumption[index][1]+numdaysinmonth
			monthlyconsumption[index][2] = True
		
			date1 = getNextMonth(date1)

		#do the final month
		numdays = date2.day-1
		if(numdays > 0):  			
			index = getMonthIndex(date2, startdate)
			print "index for "+str(date2)+" is worked out as "+str(index)+" and num billing days is "+str(numdays)
			consumptionthismonth=dailycons*numdays
			monthlyconsumption[index][0] = monthlyconsumption[index][0]+consumptionthismonth
			monthlyconsumption[index][1] = monthlyconsumption[index][1]+numdays
			monthlyconsumption[index][2] = True
		
		
	print str(startdate)+" --> "+str(enddate)
	print monthlyconsumption
	raw_input("press enter to continue")		
			
			

			
			
	
    
        


	

























