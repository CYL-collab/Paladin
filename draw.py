import json
import networkx as nx
import matplotlib.pyplot as plt

# 读取json文件
with open('graph-com.flauschcode.broccoli.json') as f:
    data = json.load(f)['activities']
    activities = {data[i]['activity_name']: data[i]['fragments']  for i in range(len(data))}
    fragments = [data[i]['fragments'][j] for i in range(len(data)) for j in range(len(data[i]['fragments']))]
# 创建一个空的有向图
G = nx.MultiDiGraph()

# 添加节点
for i, fragment in enumerate(fragments):
    G.add_node(fragment['signature'])

# 添加边
for fragment in fragments:
    for path in fragment['allPaths']:
        G.add_edge(fragment['signature'], path['target'], path=path['path'])

for cycle in nx.simple_cycles(G):
    print(cycle)
    
# 绘制图形
# nx.draw(G)
# plt.show()