import numpy as np
from scipy.sparse import csr_matrix

a = np.array([[1, 0, 2],
       [0, 0, 3],
       [4, 5, 6]])
b = csr_matrix(a)
print(b.data)
print(b.indices)
print(b.indptr)