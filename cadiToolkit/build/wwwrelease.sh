#!/bin/sh

if [ $# -ne 1 ]; then
	echo usage is $0 RELEASE_NUM
	exit 0
fi

wwwbase=~/www/pub/cadi/caditk/softwarerepository
sdir=$wwwbase/$1

if [ -d $sdir ]; then
	echo directory $sdir already exists
	echo removing it.
	rm -r $sdir
fi

echo installing release $1
cp -r build $sdir
rm $wwwbase/current
ln -s $sdir $wwwbase/current

