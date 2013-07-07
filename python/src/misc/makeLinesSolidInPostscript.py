import sys



lines=open(sys.argv[1]).readlines()


for line in lines:
	if(line.startswith("/LT") and line.split()[1] == "{" and line.split()[2] == "PL" and line.find("dl") > 0):
		printstr = ""
		inprint = True
		for i in range(0, len(line)):
			if(line[i] == "]"):
				inprint = True

			if(inprint):
				printstr = printstr + line[i]
			
			if(line[i] == "["):
				inprint = False
		print printstr
	else:
		print line.strip()
