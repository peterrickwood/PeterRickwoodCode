#!/bin/sh
lganum=$1

echo "getting LGA $1"

hreftext=`wget --timeout=8 -O - "http://www8.abs.gov.au/ABSNavigation/prenav/ViewData?&action=404&documentproductno=LGA$1&documenttype=Details&tabname=Details&areacode=LGA$1&issue=2001&producttype=Community%20Profiles&&producttype=Community%20Profiles&javascript=false&textversion=false&navmapdisplayed=false&breadcrumb=LPD&#Expanded%20Community%20Profile" | grep -i expanded | grep -i community | grep -i profile`

echo hreftext is $hreftext

url=`python ~/scripts/CDutils.py extractLGAfromHTML $hreftext`

echo url is $url

wget $url

