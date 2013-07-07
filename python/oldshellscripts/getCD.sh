#!/bin/bash


#if the file already exists (i.e. has already been downloaded, we skip that part
if [ -f BCP_$1.xls ]; then
	echo "Excel file already present... skipping download"	
else #otherwise we get it

	echo "getting $1"

	#first we work out the URL
	hreftext=`wget --timeout=8 -O - "http://www.abs.gov.au/ausstats/abs@cpp.nsf/DetailsPage/$12001?OpenDocument&tabname=Details&prodno=$1&issue=2001&num=&view=&#Basic%20Community%20Profile" | grep -i basic | grep -i community | grep -i profile | grep -i href | grep -i ownload`

	echo hreftext is $hreftext

	url=`python ~/scripts/CDutils.py extractBCPURLfromHTML $hreftext`
	echo "URL is $url"

	#now we get it and unzip it
	wget $url
	unzip BCP_$1.zip
	rm BCP_$1.zip
fi



#now extract information from it
dirname="BCP_$1_pages"
if [ -d BCP_$1_pages ]; then
	echo "CD data has already been processed"
elif [ -f BCP_$1.xls ]; then
	mkdir $dirname
	page=1
	while [ $page -lt 46 ]; 
	do
		echo "extracting page $page"
		xlhtml -asc -xp:$page BCP_$1.xls > $dirname/BCP_$1.$page
		page=`expr $page + 1`
	done
else
	echo ERROR.. no excel file downloaded for CD $1
fi




