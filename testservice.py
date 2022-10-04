import subprocess
#curl -v --form input=@./thefile.pdf  20.235.240.75:8070/api/processFulltextDocument

def run():
    output = subprocess.run(['curl', '-v', '--form', 'input=@./bigsize_test.pdf', '20.235.240.75:8070/api/processFulltextDocument'])
    return output

from multiprocessing.pool import ThreadPool as Pool
# from multiprocessing import Pool

pool_size = 15  

try:
    while(True):

        pool = Pool(pool_size)

        for _ in range(50):
            pool.apply_async(run)

        pool.close()
        pool.join()

except:
    pass
