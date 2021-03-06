'''
Created on Jul 2, 2012

@author: frank
'''
import base64
import os
import os.path
import sys

HTML_ROOT = "/var/www/html/"


def writeIfNotHere(fileName, texts):
    if not os.path.exists(fileName):
        entries = []
    else:
        f = open(fileName, 'r')
        entries = f.readlines()
        f.close()

    texts = ["%s\n" % t for t in texts]
    need = False
    for t in texts:
        if not t in entries:
            entries.append(t)
            need = True

    if need:
        f = open(fileName, 'w')
        f.write(''.join(entries))
        f.close()


def createRedirectEntry(vmIp, folder, filename):
    entry = "RewriteRule ^%s$  ../%s/%%{REMOTE_ADDR}/%s [L,NC,QSA]" % (filename, folder, filename)
    htaccessFolder = "/var/www/html/latest"
    htaccessFile = os.path.join(htaccessFolder, ".htaccess")
    if not os.path.exists(htaccessFolder):
        os.makedirs(htaccessFolder)
    writeIfNotHere(htaccessFile, ["Options +FollowSymLinks", "RewriteEngine On", entry])

    htaccessFolder = os.path.join("/var/www/html/", folder, vmIp)
    if not os.path.exists(htaccessFolder):
        os.makedirs(htaccessFolder)
    htaccessFile = os.path.join(htaccessFolder, ".htaccess")
    entry = "Options -Indexes\nOrder Deny,Allow\nDeny from all\nAllow from %s" % vmIp
    f = open(htaccessFile, 'w')
    f.write(entry)
    f.close()

    if folder in ['metadata', 'meta-data']:
        entry1 = "RewriteRule ^meta-data/(.+)$  ../%s/%%{REMOTE_ADDR}/$1 [L,NC,QSA]" % folder
        htaccessFolder = "/var/www/html/latest"
        htaccessFile = os.path.join(htaccessFolder, ".htaccess")
        entry2 = "RewriteRule ^meta-data/$  ../%s/%%{REMOTE_ADDR}/meta-data [L,NC,QSA]" % folder
        writeIfNotHere(htaccessFile, [entry1, entry2])


def addUserData(vmIp, folder, fileName, contents):
    baseFolder = os.path.join(HTML_ROOT, folder, vmIp)
    if not os.path.exists(baseFolder):
        os.makedirs(baseFolder)

    createRedirectEntry(vmIp, folder, fileName)

    datafileName = os.path.join(HTML_ROOT, folder, vmIp, fileName)
    metaManifest = os.path.join(HTML_ROOT, folder, vmIp, "meta-data")
    if folder == "userdata":
        if contents != "none":
            contents = base64.urlsafe_b64decode(contents)
        else:
            contents = ""

    f = open(datafileName, 'w')
    f.write(contents)
    f.close()

    if folder == "metadata" or folder == "meta-data":
        writeIfNotHere(metaManifest, fileName)


if __name__ == '__main__':
    string = sys.argv[1]
    allEntires = string.split(";")
    for entry in allEntires:
        (vmIp, folder, fileName, contents) = entry.split(',', 3)
        addUserData(vmIp, folder, fileName, contents)
    sys.exit(0)
