
#
#
#



lines=open('/home/peterr/data/gislayers/cd_to_tz_results.txt').readlines()
lines = [line for line in lines if line[0] != "#"]

#each line is:
#
# CDNUM , TZNUM : count , TZNUM : count , ... , TOTALCOUNT
for line in lines:
	bits = line.split(",")
	cdnum = bits[0].strip()
	total = float(bits[-1])

	#get the tuples of (tznum, count)
	tztup = [(int(item.split(":")[0]) , int(item.split(":")[1])) for item in bits[1:-1]]


	#If less than 5% of the area of the CD is split across multiple 
	#TZs, then we just assign it to the majority TZ
	majtz = max([item[1] for item in tztup])
	if(majtz >= 0.95*total):
		majindex = [item[1] for item in tztup].index(majtz)
		print cdnum+" ,  "+str(tztup[majindex][0])+" : 1.0"
		continue

	#otherwise, we do the calculation
	#
	#we exclude any TZ that has less than 5% of the CD
	thresh = 0.05*total
	tztup = [item for item in tztup if item[1] >= thresh]
	newtot = float(sum([item[1] for item in tztup]))
	printstr = cdnum
	for item in tztup:
		printstr = printstr + " , " + str(item[0]) + " : "+str(item[1]/newtot)
	print printstr






