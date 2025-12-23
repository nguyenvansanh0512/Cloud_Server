#include <iostream>
#include <vector>
using namespace std;

vector<int> x; // mảng lưu nghiệm nhị phân
int n = 3;

void Try(int i) {
    cout << "Goi Try(" << i + 1 << ") :" << endl;

    for (int j = 0; j <= 1; j++) {
        cout << "\t\t\t j= " << j << endl;
        x[i] = j;
        cout << "\t\t\t x" << i + 1 << " = " << j << endl;

        if (i == n - 1) {
            cout << "\t\t\t i = " << i + 1 << " dung -> ";
            for (int h = 0; h < n; h++)
                cout << x[h];
            cout << endl;
        } else {
            cout << "\t\t\t i = " << i + 1 << " sai" << endl;
            Try(i + 1);
        }
    }

    cout << "\t\t\t j= " << 2 << " sai thoat Try(" << i + 1 << ")" << endl;
    if (i > 0)
        cout << "Quay lui Try(" << i << ") :" << endl;
}

int main() {
    x.resize(n, 0); // khởi tạo mảng x n phần tử = 0
    Try(0);
    return 0;
}
