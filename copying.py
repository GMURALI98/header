from email import header
import os
import glob
import shutil


from numpy import full

input_folder = "./out"
s = os.listdir(input_folder)
src = input_folder
segmentation_fldr = ("./output1/segmentation")
fulltext_fldr = ("./output1/fulltext")
table_fldr = ("./output1/table")
header_fldr = ("./output1/header")

if not os.path.exists("./output1"):
    os.makedirs("output1")

if not os.path.exists(segmentation_fldr):
    os.makedirs(segmentation_fldr)
    os.makedirs(segmentation_fldr + "/tei")
    os.makedirs(segmentation_fldr + "/raw")

if not os.path.exists(fulltext_fldr):
    os.makedirs(fulltext_fldr)
    os.makedirs(fulltext_fldr + "/tei")
    os.makedirs(fulltext_fldr + "/raw")

if not os.path.exists(table_fldr):
    os.makedirs(table_fldr)
    os.makedirs(table_fldr + "/tei")
    os.makedirs(table_fldr + "/raw")

if not os.path.exists(header_fldr):
    os.makedirs(header_fldr)
    os.makedirs(header_fldr + "/tei")
    os.makedirs(header_fldr + "/raw")

for i in s:
    if i.endswith(".training.segmentation.tei.xml"):
        shutil.copy(os.path.join(src, i), segmentation_fldr+"/tei")
    if i.endswith(".training.segmentation"):
        shutil.copy(os.path.join(src, i), segmentation_fldr + "/raw")
    if i.endswith(".training.fulltext.tei.xml"):
        shutil.copy(os.path.join(src, i), fulltext_fldr + "/tei")
    if i.endswith(".training.fulltext"):
        shutil.copy(os.path.join(src, i), fulltext_fldr + "/raw")
    if i.endswith(".training.segmentation.tei.xml"):
        shutil.copy(os.path.join(src, i), segmentation_fldr+"/tei")
    if i.endswith(".training.table"):
        shutil.copy(os.path.join(src, i), table_fldr + "/raw")
    if i.endswith(".training.table.tei.xml"):
        shutil.copy(os.path.join(src, i), table_fldr + "/tei")

    if i.endswith(".training.header"):
        shutil.copy(os.path.join(src, i), header_fldr + "/raw")
    if i.endswith(".training.header.tei.xml"):
        shutil.copy(os.path.join(src, i), header_fldr + "/tei")
