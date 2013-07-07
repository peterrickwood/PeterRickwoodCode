#join 2 files by key
import sys


def join(file1, f1delimiter, f1joinkeys, file2, f2delimiter, f2joinkeys, f2targetkeys):
    """
    Join the records in the files (file 1 and file 2) by matching
    f1joinkeys with f2joinkeys, and appending the value for each key in 
    f2targetkeys.

    EACH FILE MUST HAVE A HEADER LINE WITH THE COLUMN NAMES
    
    So, for instance, if file 1 is of this format:
    ID, X, Y, Z

    And file 2 is of this format:
    BLAH, X, Q

    And you do a 'join' operation with join(file1, ",", "X", file2, "," "X", ["Q"])
    then for each line in file1, if the X column matches the X column in file2, 
    the Q value from file2 is appended

    Results (of the join) are written to standard output
    """

    #listify join key arguments if there are only one of them
    if type(f1joinkeys) != type([]):
        f1joinkeys = [f1joinkeys]
    if type(f2joinkeys) != type([]):
        f2joinkeys = [f2joinkeys]
    f1joinkeys.sort()
    f2joinkeys.sort()   

 
    f1 = open(file1)
    f1header = f1.readline().strip()
    f2 = open(file2)
    f2header = f2.readline().strip()

    f1columnindexmap = {}
    for item in f1header.split(f1delimiter):
        f1columnindexmap[item] = f1header.split(f1delimiter).index(item)
    f2columnindexmap = {}
    for item in f2header.split(f2delimiter):
        f2columnindexmap[item] = f2header.split(f2delimiter).index(item)
        

    
    f2idvalmap = {}
    for line in f2:
        bits = line.strip().split(f2delimiter)
        joinids = [bits[f2columnindexmap[key]].strip() for key in f2joinkeys]
        joinid = "+".join(joinids)
        values = [bits[index] for index in [f2columnindexmap[item] for item in f2targetkeys]]
        f2idvalmap[joinid] = values        

    for line in f1:
        bits = line.strip().split(f1delimiter)
        joinids = [bits[f1columnindexmap[key]].strip() for key in f1joinkeys]
        joinid = "+".join(joinids)
        if f2idvalmap.has_key(joinid):
            bits = bits + f2idvalmap[joinid]
        else:
            bits.append("***JOINFAILED***")
        print f1delimiter.join(bits)


def usage():
    print "Usage:"
    print "    python join.py FILE1 FILE2 delimiter joinkey[,joinkey2,...] fieldtoappend [fieldstoappend]"
    print ""
    print "Note that because joinkeys are comma-separated in the arguments above, a limitation is that you cannot currently have joinkeys with commas in them."


if __name__ == "__main__":
    if len(sys.argv) < 6:
        usage()
    else:
        f1 = sys.argv[1]
        f2 = sys.argv[2]
        delimit = sys.argv[3]
        joinkeys = sys.argv[4]
        if joinkeys.find(",") == 0:
            raise Exception("joinkeys cannot start with a comma")
        elif joinkeys.find(",") > 0:
            joinkeys = joinkeys.split(",")

        fields = sys.argv[5:]
        join(f1, delimit, joinkeys, f2, delimit, joinkeys, fields)


