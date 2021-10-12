
import math
class OSDataflow:
    def __init__(self, x, k):
        self.x=x
        self.k=k
        self.c=x
    
    def mapping(self, xx, kk, cc, ker, stride):
        tile_x = math.ceil(xx /self.x)
        tile_k = math.ceil(kk /self.k)
        calc_num = self.x * tile_x * self.k * tile_k * (max(cc * ker * ker, self.c)) * xx  * stride
        workload = xx * kk * cc * ker * ker
        return calc_num

class WSDataflow:
    def __init__(self, c, k):
        self.x=c
        self.k=k
        self.c=c

    def mapping(self, xx, kk, cc, ker):
        tile_c = math.ceil(cc /self.c)
        tile_k = math.ceil(kk /self.k)
        calc_num = self.c * tile_c * self.k * tile_k * (max(xx * xx * ker * ker, self.x) )
        workload = xx * kk * cc * ker * ker
        return calc_num

def workload(xx, kk, cc, ker):
    return xx * xx * kk * cc * ker * ker

pe_x = 32
pe_y = 32
dv = 2
os = OSDataflow(pe_x, pe_y)
ws = WSDataflow(pe_x, pe_y)

net = []
txt = ""
with open("yolov3") as f:
    txt = f.readlines()

layer = {}
min_total = 0
ws_total = 0
best_total = 0
os_better = 0
ws_better = 0
for line in txt:
    if line == "\n":
        #print(layer)
        ow = int(layer["OUTPUT_W"])
        oc = int(layer["OUTPUT_C"])/dv
        ic = int(layer["INPUT_C"])/dv * 0.3
        ker = int(layer["KERNEL_SIZE"])
        stride = int(layer["STRIDE"])
        os_work = os.mapping(ow, oc, ic, ker, stride)
        ws_work = ws.mapping(ow, oc, ic, ker)
        min_work = workload(ow, oc, ic, ker)
        if os_work < ws_work:
            os_better += 1
        else:
            ws_better += 1
        print("WORKLOAD=%d, OS=%d, WS=%d"%(min_work, os_work, ws_work))
        #print("OS util:%f, WS util: %f"%(min_work/os_work, min_work / ws_work) )
        min_total += min_work
        ws_total += ws_work
        best_total += min(os_work, ws_work)
        net += layer
        layer = {}
    else:
        item = line.strip("\n").split(" ")
        layer[item[0]]=item[1]
    
print("workload = %d, WS = %d, opt = %d, %f, %f"%(min_total, ws_total, best_total, min_total / best_total , min_total / ws_total))
print("os_better: %d, ws_better: %d"%(os_better, ws_better))