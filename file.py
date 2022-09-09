import os

from numpy import diff

raw = os.listdir("./grobid-trainer/resources/dataset/header/corpus/raw")
tei = os.listdir("./grobid-trainer/resources/dataset/header/corpus/tei")

for i in range(len(raw)):
    raw[i] = raw[i].split(".training.header")[0]


for i in range(len(tei)):
    tei[i] = tei[i].split(".training.header.tei.xml")[0]


differ = (list(set(tei).difference(set(raw))))
for i in differ:
    print(i)
print(len(raw))
print(len(tei))
print(len(differ))
