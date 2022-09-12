import os
path = "./grobid-trainer/resources/dataset/fulltext/corpus/tei/"
list_of_files = os.listdir(path)

print(list_of_files)
print(len(list_of_files))
for i in list_of_files:
    training_file = path + i
    print("checking", training_file, "...")

    lines = {}
    with open(training_file) as fp:
        for cnt, line in enumerate(fp):
            if len(line.strip()) == 0:
                continue
            if line.find(" ") == -1:
                pieces = line.split("\t")
            else:
                pieces = line.split(" ")
            if len(pieces) in lines:
                lines[len(pieces)] += 1
            else:
                lines[len(pieces)] = 1

    # report
    expected = 0
    for key in lines:
        if lines[key] > expected:
            expected = int(key)

    with open(training_file) as fp:
        for cnt, line in enumerate(fp):
            if len(line.strip()) == 0:
                continue
            if line.find(" ") == -1:
                pieces = line.split("\t")
            else:
                pieces = line.split(" ")
            if len(pieces) != expected:
                print("line", cnt, "- number of features", len(pieces),
                      "(expected", str(expected)+"):", line.replace("\n", ""))

    # report
    expected = 0
    for key in lines:
        print(key, lines[key])
        if lines[key] > expected:
            expected = int(key)
    print("expected number of features per line:", expected)
