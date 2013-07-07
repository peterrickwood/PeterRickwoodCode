
#create synthetic unit records from census data at the CD level
#
#
#The variables that I am interested in are 
#
#1) HHTYPE
#2) HHINCOME
#3) DWELLING_TYPE
#4) #CARS


import CDutils
import random


#sample from a probability distribution
def samplefrom(pd):
	val = random.random()
	tot = 0.0
	i = 0
	while(tot+pd[i] < val):
		tot = tot+pd[i]
		i = i+1
	return i



def normalize(l):
	tot = sum(l)
	for i in xrange(0, len(l)):
		l[i] = l[i]/tot
	return l

def getTuples(cdnum, startindices, dwellbyhhtypelines):
	tuples = []
	start = startindices[str(cdnum)][1]

	dwelltypes = ["house", "semi", "lowriseunit", "unit"]
	hhtypes = ["single", "group", "singleparent", "couplenokids", "couplewkidslt15", "couplewkidsgt15", "otherfamily", "other"]
	print hhtypes

	#get <hhtype, dwelltype> tuples
	for i in range(0, len(dwelltypes)):
		print dwellbyhhtypelines[start+i]
		counts = map(int, dwellbyhhtypelines[start+i].split(",")[2:-1])
		#replace all '3's with '2's
		for j in range(0, len(counts)):
			if(counts[j] == 3):
				counts[j] = 2
		
		#ok, now we have a count of each of the number of 
		#household types in this dwelling type
		#enumerate them all
		for j in range(0, len(hhtypes)):
			num = counts[j]
			for k in range(0, num):
				tuples.append([hhtypes[j], dwelltypes[i]])

	print tuples
	return tuples


def boost(dist, numtoboostby):
	print dist
	print numtoboostby
	tot = sum(dist)
	
	if(tot == 0):
		return dist  #dont try and boost empty distributions

	multiplier = (tot+numtoboostby)/float(tot)

	newdist = [item*multiplier+random.random()/1000.0 for item in dist]	
	intdist = [int(round(item)) for item in newdist]
	for i in xrange(0, len(newdist)):
		newdist[i] = newdist[i]-intdist[i]

	newshortfall = (tot+numtoboostby-sum(intdist))
	while(newshortfall > 0):
		maxindex = newdist.index(max(newdist))
		intdist[maxindex] = intdist[maxindex]+1
		newdist[maxindex] = newdist[maxindex]-1
		newshortfall = newshortfall -1
		
	return intdist	


#attribute an income to each household	
#
#we do this by using the dwellbyincomecount to probabilistically assign
#income, while maintaining hhtypebyincomecounts consistency
#
#
#hhtypebyincomecounts is a map which maps each hhtype to an array of income counts
#
#dwellbyincomecounts is a map which maps dwelling type to an array of income counts
def appendIncome(tups, cdnum, startindices, hhtypebyincomecounts, dwellbyincomecounts):
	
	#we need to make sure that there are enough hhtypebyincome counts
	#to cover all tuples
	nonfam = [item for item in tups if (item[0] == 'single' or item[0] == 'group' or item[0] == 'other')]
	couplewkids = [item for item in tups if (item[0] == 'couplewkidslt15' or item[0] == 'couplewkidsgt15')]
	couplenokids = [item for item in tups if (item[0] == 'couplenokids')]
	singleparent = [item for item in tups if (item[0] == 'singleparent')]
	otherfamily = [item for item in tups if (item[0] == 'otherfamily')]

	#boost them all so that they have enough
	hhtype = 'nonfamily'
	shortfall = sum(hhtypebyincomecounts[hhtype])-len(nonfam)
	print "need "+str(len(nonfam))+" "+hhtype+" households. Are short "+str(shortfall)
	if(shortfall < 0):
		hhtypebyincomecounts[hhtype] = boost(hhtypebyincomecounts[hhtype], abs(shortfall))
	
	hhtype = 'couplewkids'
	shortfall = sum(hhtypebyincomecounts[hhtype])-len(couplewkids)
	print "need "+str(len(couplewkids))+" "+hhtype+" households. Are short "+str(shortfall)
	if(shortfall < 0):
		hhtypebyincomecounts[hhtype] = boost(hhtypebyincomecounts[hhtype], abs(shortfall))

	hhtype = 'couplenokids'
	shortfall = sum(hhtypebyincomecounts[hhtype])-len(couplenokids)
	print "need "+str(len(couplenokids))+" "+hhtype+" households. Are short "+str(shortfall)
	if(shortfall < 0):
		hhtypebyincomecounts[hhtype] = boost(hhtypebyincomecounts[hhtype], abs(shortfall))

	hhtype = 'singleparent'
	shortfall = sum(hhtypebyincomecounts[hhtype])-len(singleparent)
	print "need "+str(len(singleparent))+" "+hhtype+" households. Are short "+str(shortfall)
	if(shortfall < 0):
		hhtypebyincomecounts[hhtype] = boost(hhtypebyincomecounts[hhtype], abs(shortfall))

	hhtype = 'otherfamily'
	shortfall = sum(hhtypebyincomecounts[hhtype])-len(otherfamily)
	print "need "+str(len(otherfamily))+" "+hhtype+" households. Are short "+str(shortfall)
	if(shortfall < 0):
		hhtypebyincomecounts[hhtype] = boost(hhtypebyincomecounts[hhtype], abs(shortfall))
	
	
	#ok, now complete the tuples
	for tup in tups:
		if(tup[0] == 'single' or tup[0] == 'group' or tup[0] == 'other'):
			hhtype = 'nonfamily'
		elif(tup[0] == 'couplewkidslt15' or tup[0] == 'couplewkidsgt15'):
			hhtype = 'couplewkids'
		else:
			hhtype = tup[0]
		dwelltype = tup[1]
		
		#get income from dwelling type, while there is a slot left
		#for it
		#
		#now, it can occasionally be the case that the ABS tables
		#dont exactly match up. This can result in, for example,
		#there being a (single, semi) household, but no entries
		#in the semi bracket for income. (Wierd, huh).
		if(sum(hhtypebyincomecounts[hhtype]) == 0):
			tup.append(-1) #put in `dont know'

		else:
			pdist1 = normalize([float(item) for item in hhtypebyincomecounts[hhtype]])
			pdist2 = [float(item) for item in dwellbyincomecounts[dwelltype]]
			if(sum(pdist2) <= 0.5): #no entries for this dwelling type!, even though we have a household in that dwelling
				print "ABSERROR: No entries for dwelling type "+dwelltype+" in global dwellbyincome table, even though hhtypebydwelltype has a "+hhtype+" household in such a dwelling"
				pdist2=[1.0/len(pdist1)]*len(pdist1) #just pick a remaining income at random
			else:
				normalize(pdist2)	
		
			pdist3 = []
			for i in xrange(0, len(pdist1)):
				pdist3.append(pdist1[i]*pdist2[i])
			if(sum(pdist3) <= 0.001): #income and dwelltype incomes dont match! ABS error
				print "ABSERROR: hhtypebyincome does not agree with dwelltypebyincome -- household in dwelling "+dwelltype+" cannot have one of the remaining incomes for that household, because no household in that dwelling type has such an income!"
				pdist3 = pdist1 #just pick a remaininng income based on dwelling type
			normalize(pdist3)
		
			index = samplefrom(pdist3)
			tup.append(index) #create income index	
			#update counts
			hhtypebyincomecounts[hhtype][index] = hhtypebyincomecounts[hhtype][index]-1

		print "HH: "+str(cdnum)+" "+str(tup)
		

	#and we're done
	return


#just get the distribution of income for each dwelling type
def getdwellbyincome(cdnum, startindices, dwellbyincomelines):
	res = {}
	start = startindices[str(cdnum)][0]
	print "start for CD "+str(cdnum)+" in modified dwellbyincome lines is "+str(start)

	res['house'] = dwellbyincomelines[start].split(",")[2:-2]
	res['semi'] = dwellbyincomelines[start+1].split(",")[2:-2]
	res['lowriseunit'] = dwellbyincomelines[start+2].split(",")[2:-2]
	res['unit'] = dwellbyincomelines[start+3].split(",")[2:-2]

	for x in ['house','semi','lowriseunit','unit']:
		resl = res[x]
		for i in xrange(0, len(resl)):
			if int(resl[i]) == 3:
				resl[i] = 3
			else:
				resl[i] = int(resl[i])+1
	

	print res
	return res
		





def gethhtypebyincome(cdnum):
	famincomelines = open("/home/peterr/data/abs_stats/2001census/BCP_"+str(cdnum)+"_pages/BCP_"+str(cdnum)+".42").readlines()
	famnonfamlines = open("/home/peterr/data/abs_stats/2001census/BCP_"+str(cdnum)+"_pages/BCP_"+str(cdnum)+".43").readlines()

	allcounts = []

	for i in range(9, 9+13):
		line = famincomelines[i]
		bits = line.split()
		dolstr = " ".join(bits[0:len(bits)-5])

		counts = bits[-5:-1]
		for i in xrange(0, len(counts)):
			if(int(counts[i]) == 3):
				counts[i] = 2
			else:
				counts[i] = int(counts[i])
	
		allcounts.append(counts)

	print "here are the counts (in increasing income order) across family types"
	print allcounts	


	#combine the first 4 entries into 2 entries
	newallcounts = []
	newallcounts.append(allcounts[0])
	for i in xrange(0, len(allcounts[1])):
		newallcounts[0][i] = newallcounts[0][i]+allcounts[1][i]
	newallcounts.append(allcounts[2])
	for i in xrange(0, len(allcounts[3])):
		newallcounts[1][i] = newallcounts[1][i]+allcounts[3][i]

	
	for i in xrange(4, len(allcounts)):
		newallcounts.append(allcounts[i])
	
	print "after combining first 2 brackets, these are:"
	print newallcounts

	#now we have counts for each income range for each household type.
	

	#put in all the information we have on income by family type
	res = {}
	res['couplewkids'] = [item[0] for item in newallcounts]
	res['couplenokids'] = [item[1] for item in newallcounts]	
	res['singleparent'] = [item[2] for item in newallcounts]	
	res['otherfamily'] = [item[3] for item in newallcounts]	
	

	#now do non-family households
	nonfamcounts = []
	for i in range(9, 9+13):
		line = famnonfamlines[i]
		bits = line.split()
		dolstr = " ".join(bits[0:len(bits)-3])

		count = bits[-2:-1][0]
		if(int(count) == 3):
			count = "2"
	
		nonfamcounts.append(int(count))

	res['nonfamily'] = []
	res['nonfamily'].append(nonfamcounts[0]+nonfamcounts[1])
	res['nonfamily'].append(nonfamcounts[2]+nonfamcounts[3])
	for i in xrange(4, len(nonfamcounts)):
		res['nonfamily'].append(nonfamcounts[i])
	

	print res
	return res


def processCD(cdnum, startindices, dwellbyincomelines, dwellbyhhtypelines, carsbyhhtypelines, carsbyincomelines):
	#if the CD does not have any households, we skip it
	if 'households' in CDutils.cddat[int(cdnum)].keys():
		if CDutils.cddat[int(cdnum)]['households'] < 50 :
			print "CD "+str(cdnum)+" has < 50 households.... skipping"
			return None
	else:
		print "CD "+str(cdnum)+" has no KEY for 'households'..... skipping"
		return None
	
	#only get here if there are greater than 0 households, and we have
	#a `households' key

	if(not(str(cdnum) in startlines.keys())):
		print("No cross-tab info for "+str(cdnum))
		return None
	elif(len(startlines[str(cdnum)]) != 4):
		print("Incomplete cross-tab info for "+str(cdnum))
		return None
	
	#start by forming <hhtype, dwelltype> tuples within the CD
	tups = getTuples(cdnum, startindices, dwellbyhhtypelines)

	#now try and match up income
	hhtypebyincomecounts = gethhtypebyincome(cdnum)
	dwellbyincomecounts = getdwellbyincome(cdnum, startindices, dwellbyincomelines)

	appendIncome(tups, cdnum, startindices, hhtypebyincomecounts, dwellbyincomecounts)

	print "Done CD "+str(cdnum)



def getStartLines(dwellbyincomelines, dwellbyhhtypelines, carsbyhhtypelines, carsbyincomelines):
	startlines = {}
	for f in [dwellbyincomelines, dwellbyhhtypelines, carsbyhhtypelines, carsbyincomelines]:
		i = 0
		while i < len(f):
			cdnum = f[i].split(",")[0].strip()
			mini = i
			i = i + 1
			while i < len(f) and f[i].split(",")[0].strip() == cdnum :
				i = i+1

			if f == dwellbyincomelines:
				startlines[cdnum] = [mini]
			else:
				startlines[cdnum].append(mini)
	return startlines



#strip away all the commas that appear within quotes 
def stripQuotedCommas(lines):
	for x in xrange(0, len(lines)):
		line = lines[x]
		inquote = 0
		newline = ""
		for i in xrange(0, len(line)):
			if line[i] == "\"":
				inquote = not(inquote)
				newline = newline+line[i]
			elif(inquote and line[i] == ","):
				newline = newline+" "
			else:
				newline = newline+line[i]
		lines[x] = newline

#testcds = [1421501, 1421502, 1421503, 1421504, 1421505]


#load all the Sydney CD census data
#CDutils.getSomeSydneyCDs(testcds)
CDutils.getAllSydneyCDs()


#now load the specific cross-tabs that I have from the ABS
dwellbyincomelines = open('/home/peterr/data/abs_stats/sydneyhousingspecialrequest/Rickwood Table 1 190407.csv').readlines()[7:]
dwellbyhhtypelines = open('/home/peterr/data/abs_stats/sydneyhousingspecialrequest/Rickwood Table 2 190407.csv').readlines()[6:]
carsbyhhtypelines = open('/home/peterr/data/abs_stats/sydneyhousingspecialrequest/Rickwood Table 3 190407.csv').readlines()[6:]
carsbyincomelines = open('/home/peterr/data/abs_stats/sydneyhousingspecialrequest/Rickwood Table 4 190407.csv').readlines()[7:]
for x in [dwellbyincomelines, dwellbyhhtypelines, carsbyhhtypelines, carsbyincomelines]:
	stripQuotedCommas(x)

#work out starting data lines for each cdnumber, to save searching each time
startlines = getStartLines(dwellbyincomelines, dwellbyhhtypelines, carsbyhhtypelines, carsbyincomelines)



#for cd in testcds:
#	processCD(cd, startlines, dwellbyincomelines, dwellbyhhtypelines, carsbyhhtypelines, carsbyincomelines)


#process each CD independently
for key in CDutils.cddat.keys():
	processCD(key, startlines, dwellbyincomelines, dwellbyhhtypelines, carsbyhhtypelines, carsbyincomelines)



