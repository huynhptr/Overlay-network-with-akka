import collections
import matplotlib.pyplot as plt
import math

file1 = "sample_CAN_one.yaml"
file2 = "sample_CAN_two.yaml"
file3 = "sample_CAN_three.yaml"
lsts =[] 
files = [file1,file2,file3]
for name in files:
    with open(name,"r") as f:
        lst = []
        for line in f:
            
            if "Time" in line:
                time = int(line.split(":")[1].strip().replace("ms",""))
                lst.append(time)
        lsts.append(lst)

i = 0

for name in files:
    imgName = name.split(".")[0]+"_image.png"
    plt.plot(lsts[i])
    plt.ylabel("Request Time (ms)").set_style('italic')
    plt.xlabel("Simulation Duration").set_style('italic')
    plt.grid('on')
    plt.axis(xmin=0.0,xmax=500)
    plt.axis(ymin=0.0, ymax=250)
    plt.title('Performance Over Network Lifetime').set_weight('bold')
    i+=1

# plt.plot([math.log((i+1)**10) for i in range(980)])
plt.plot([i*.1+20 for i in range(980)])
plt.plot([math.sqrt(i*10) for i in range(980)])
plt.legend(['500 RPM','500,000 RPM',"5,000,000 RPM", "O(N)", "O(SQRT)"])
plt.savefig("performance_image.png")

