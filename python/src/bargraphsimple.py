import math

boxwidth=0.5
plotcmds=["set terminal postscript eps enhanced color","set output \"tmpfl.eps\"","set title \"TITLE\"", "set ylabel \"yaxis\"", "unset xtics" , "set notics" , "set boxwidth "+str(boxwidth)]


datafile=open("test.dat")
datalines=datafile.readlines()

vals=[]
maxv=-999999999
for line in datalines:
	v=line.split()[1]
	if(float(v) > maxv):
		maxv=float(v)	
	vals.append(line.split()[1])

plotcmds.append("set xrange [0:"+str(math.floor(1+boxwidth*len(datalines)))+"]")
plotcmds.append("set yrange [0:"+str(round(maxv*1.1+1))+"]")

datline=''
for val in vals:
	datline = datline + val + ' '
f=open("tmpfl.dat", "w")
f.writelines([datline+"\n"])
f.close()


plotline = 'plot '

#plot a bar graph from the data. Each data line must be a single title followed
#by a single data value
i=0
for line in datalines:
	title = line.split()[0]
	val = float(line.split()[1])
	lwr=i*boxwidth+boxwidth
	plotline = plotline + ' "tmpfl.dat" using ('+str(lwr)+'):($'+str(i+1)+') w boxes fill solid title "'+title+'" , '
	i=i+1
	

plotline = plotline[0:-2] #chop off last comma

plotcmds.append(plotline)

for line in plotcmds:
	print line

