import sys

if len(sys.argv) <= 1:
    print "USAGE: "
    print "       arg1: name of kml file"
    print "       arg2 (optional): 'name' field to use for polygon names"
    sys.exit(0)

lines0 = open(sys.argv[1]).readlines()
#make sure each coordinates tag starts on a new line
lines = []
for line in lines0:
    if line.find("<coordinates>") >= 0:
        #print "DEBUG "+line
        bits = line.strip().split("<coordinates>")
        lines.append(bits[0])
        for i in range(1, len(bits)):
            lines.append("<coordinates>"+bits[i])
            #print "DEBUGbits "+lines[-1]
    else:
        lines.append(line.strip())

#for line in lines:
#    print "DEBUGlines "+line


namefieldstr = ""
if(len(sys.argv) > 2):
    namefieldstr = sys.argv[2]
    

incoords = False
inplacemark = False
for line in lines:
    if(line.lower().find("<placemark>") >= 0):
        if inplacemark:
            raise Exception("nested placemarks?!")
        inplacemark = True
    if(line.lower().find("</placemark>") >= 0):
        if not inplacemark:
            raise Exception("closing tag for placemark that was never opened..?")
        else:
            inplacemark = False
    
    fieldval = ""
    if namefieldstr != "" and inplacemark and line.lower().find(namefieldstr.lower()) >= 0: #this is the name field
        #look for multiple '>' characters. If there are mopre than 2
        #then it's not just a simple line
        if line.count(">") != 2 or line.count("<") != 2:
            raise Exception("dont know how to parse line: "+line)
        fieldval=line[line.index(">")+1:line.rindex("<")].strip()
        
    
    ####
    if line.lower().count("<coordinates>") >= 1:
        index = line.lower().find("<coordinates>")
        firstbit = line[:index]
        lastindex = line.lower().find("</coordinates") 
        lastbit = line[lastindex:]

        rest = line[index+len("<coordinates>"):lastindex]
        if(firstbit != ""):
            print firstbit
        print "<coordinates>"
        for bit in rest.split():
            print bit
        print lastbit.strip()
    else:
        if fieldval != "":
            print "<customname>"+fieldval+"</customname>"
        else:
            print line.strip()    

    



