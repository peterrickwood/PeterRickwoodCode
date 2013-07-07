#!/bin/bash

if [ $# -eq 0 ] ; then
	echo "usage is either:"
	echo "  $0 getCD CENSUS_NUMBER"
	echo "         OR"
	echo "  $0 FUNCTION ARGS  , with available functions printed below"
	python ~/scripts/CDutils.py $@

elif [ $1 == "getCD" ] ; then 
	if [ $# -eq 2 ] ; then
		~/scripts/getCD.sh $2
	else
		echo "must specify getCD as function and CD number as argument"
		echo "provide no arguments for full list of python commands"
		exit 
	fi
else
	if [ -d BCP_$2_pages ] ; then
		python ~/scripts/CDutils.py $@
	else
		~/scripts/getCD.sh $2
		python ~/scripts/CDutils.py $@
	fi
fi




