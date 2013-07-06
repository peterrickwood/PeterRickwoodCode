#!/bin/sh

unalias rm


# all the utils stuff
basedir=`pwd`
echo "basedir is $basedir"

# first make sure we have a lib directory
cd build
if [ ! -d "lib" ]; then
	mkdir lib
fi

echo "compiling C headers for interfacing with fortran/c"
javah -jni -o LegacyUserFunctionHandle.h -classpath . rses.inverse.util.LegacyUserFunctionHandle

cp ../rses/inverse/util/cstubs/LegacyUserFunctionHandleImp.c .

echo "compiling C object files for interfacing with fortran/c"
cc -O2 -Wall -I. -I/usr/java/j2sdk/include -I/usr/java/j2sdk/include/linux -c LegacyUserFunctionHandleImp.c

echo "copying object files to lib directory"
mv -f LegacyUserFunctionHandleImp.o lib


echo removing unneeded files
cd $basedir
rm build/caditk.jar
rm build/build.sh
rm build/cadiclient.zip
rm build/cadiserver.zip
rm build/wwwrelease.sh

#and the RMI stuff
cd $basedir
cd build
echo building RMI classes and copying policy file
rmic -d . -v1.2 rses.util.distributed.server.ComputeNodeImpl
rmic -d . -v1.2 rses.util.distributed.server.ComputeServerImpl
cp ../*.security.policy .

echo creating jar file of rses classes
cd $basedir
cd build
rm -f caditk.jar
jar cvf caditk.jar rses


echo creating client distribution
cd $basedir
cp -r build cadiclient
rm -rf cadiclient/rses
zip -r cadiclient.zip cadiclient
rm -rf cadiclient
mv cadiclient.zip build


echo creating server distribution
cd $basedir
cp -r build cadiserver
rm -rf cadiserver/rses
rm cadiserver/CadiClient*
rm cadiserver/cadiclient.zip 
zip -r cadiserver.zip cadiserver -x \*.o .\*
rm -rf cadiserver
mv cadiserver.zip build

cd $basedir
