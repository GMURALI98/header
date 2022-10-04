from flask import Flask,request,jsonify,send_file
import requests
import os
import subprocess
import pudb
import shutil
import zipfile
import requests
from math import floor, ceil
from Act_detection import load_model

app = Flask(__name__)

@app.route("/",methods=["GET"])
def health():
    return "Server is up and running,  200"

@app.route("/createTraining",methods=["POST"])
def main():

    models = ["segmentation", "fulltext", "header", "table"]
    # models = ["table"]
    data = {}

    try:
        os.makedirs("SavingPdfs")
        os.makedirs("GrobidOutput")
    except:
        pass
        
    pdf = request.files["pdf"]
    pdf.save("./SavingPdfs/"+pdf.filename) 
    filename = pdf.filename
    filename = filename.split(".pdf")[0]

    subprocess.run(["java","-Xmx1G","-jar","grobid-core/build/libs/grobid-core-0.7.1-onejar.jar", "-gH","grobid-home", "-dIn", "./SavingPdfs","-dOut", "./GrobidOutput","-exe" ,"createTraining"])
    grobid_out = os.listdir("./GrobidOutput")

    for model in models:
        data[f"{model}_xml"] = ""
        data[f"{model}_raw"] = ""
    
        for i in grobid_out:
            if i == f"{filename}.training.{model}.tei.xml":
                xml = open(f"./GrobidOutput/{i}", "r")
                data[f"{model}_xml"] = xml.read()
            
            if i == f"{filename}.training.{model}":
                raw = open(f"./GrobidOutput/{i}", "r")
                data[f"{model}_raw"] = raw.read()
    shutil.rmtree('GrobidOutput')
    shutil.rmtree('SavingPdfs')
    
    return data


@app.route("/createTableData",methods=["POST"])
def createTableData():
    data = {"table_xml":"","table_raw":"", "currendir":""}

    try:
	    os.chdir("./grobid")
    except:
	    pass

    current = os.getcwd()
    data["currentdir"] = current
    if not os.path.exists("./saving_pdfs"):
        os.makedirs("./saving_pdfs")

    try:
        if not os.path.exists("./output_from_server"):
            os.makedirs("./output_from_server")
    except:
        pass
    pdf = request.files["pdf"]
    pdf.save("./saving_pdfs/"+pdf.filename) 
    filename = pdf.filename
    filename = filename.split(".")[0]

    subprocess.run(["java","-Xmx1G","-jar","grobid-core/build/libs/grobid-core-0.7.2-SNAPSHOT-onejar.jar", "-gH","grobid-home", "-dIn", "./saving_pdfs","-dOut", "./output_from_server","-exe" ,"createTraining"])
    # os.system("java -Xmx1G -jar grobid-core/build/libs/grobid-core-0.7.1-onejar.jar -gH grobid-home -dIn ./saving_pdfs -dOut ./output_from_server -r -exe createTraining")
    s = os.listdir("./output_from_server")
    src = "./output_from_server"

    # data = {"table_xml":"","table_raw":""}

    for i in s:
        if i == f"{filename}.training.table.tei.xml":
                    table_xml = open(f"./output_from_server/{i}","r")
                    data["table_xml"] = table_xml.read()
                
        if i == f"{filename}.training.table":
            table_raw = open(f"./output_from_server/{i}","r")
            data["table_raw"] = table_raw.read()
        

        # os.remove(f"./output_from_server/{i}")

    os.remove("./saving_pdfs/"+pdf.filename)
    try:
	    os.chdir("./..")
    except:
	    pass
    return data


@app.route("/trainmodel", methods=["POST"])
def trainmodel():
    t = []
    models = ['fulltext', 'segmentation', 'header', 'table', 'figure']

    try:
        for model in models:
            for dirs in os.listdir(f"./grobid-trainer/resources/dataset/{model}/corpus"):
                shutil.rmtree(f"./grobid-trainer/resources/dataset/{model}/corpus/{dirs}")
    except:
        t.append("no existing data")

    zip_file = request.form["zip_link"]
    r = requests.get(zip_file, stream=True)
    with open("./link", 'wb') as fd:
        for chunk in r.iter_content(chunk_size=128):
            fd.write(chunk)

    with zipfile.ZipFile("link", 'r') as zip_ref:
        zip_ref.extractall(f"./trainingData")

    os.remove("link")

    for model in models:  
        for (root,dirs,files) in os.walk("./trainingData"):
            if root == f"./trainingData/{model}/corpus":
                for dirs in os.listdir(root):
                    shutil.move(f"./trainingData/{model}/corpus/{dirs}", f"./grobid-trainer/resources/dataset/{model}/corpus/{dirs}")

    shutil.rmtree('./trainingData')
    return "200"

@app.route("/spacy",methods=["POST"])
def spacy():
    html_text = request.form["key"]
    detected_text, acts=load_model(html_text)
    
    return {"html data": detected_text, "Acts": acts}

if __name__ == "__main__":
    app.run(host="0.0.0.0",debug=True)
    
json = {"xml":" xml content","raw":" content of raw "}
