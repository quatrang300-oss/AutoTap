# AutoTap: app auto click cho Android

AutoTap làm được bốn việc:

* **Auto click thông thường** theo nhiều điểm. Bạn chỉnh được khoảng cách giữa các lần click, thời gian giữ mỗi click và số vòng lặp.
* **Ghi thao tác**: app ghi lại các lần chạm, giữ và vuốt của bạn, rồi **phát lại nhanh hơn** chỉ bằng một nút bấm. Tốc độ chỉnh từ 0,25x đến 10x, số vòng lặp chọn tuỳ ý (0 là lặp vô hạn), và đặt được thời gian nghỉ giữa các vòng.
* **Nút kích hoạt nổi**: chỉnh được kích thước (32 đến 160 dp) và độ mờ (10 đến 100%). Kéo nút đến chỗ nào tiện thì thả ở đó, app sẽ nhớ vị trí này.
* **Nhận diện hình ảnh**: chụp màn hình, khoanh vùng hình mẫu tại một vị trí. Khi hình đó xuất hiện lại ở vị trí ấy, app tự click vào hình hoặc chạy một thao tác đã ghi.

Yêu cầu **Android 11 (API 30) trở lên**. App không cần quyền "Hiển thị trên ứng dụng khác" và không cần root. Mọi thứ chạy qua **dịch vụ Trợ năng (Accessibility)**.

---

## 1. Build file APK

### Cách A: Android Studio (khuyên dùng)
1. Cài [Android Studio](https://developer.android.com/studio) bản mới nhất.
2. Chọn **File → Open** rồi mở thư mục `AutoTap`. Chờ Gradle sync xong (lần đầu sẽ tải thư viện).
3. Chọn **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. File APK nằm ở `app/build/outputs/apk/debug/app-debug.apk`. Bạn cũng có thể cắm điện thoại vào máy rồi bấm ▶ Run để cài thẳng.

### Cách B: GitHub, không cần cài gì
1. Tạo một repo GitHub mới và đẩy toàn bộ thư mục này lên (có sẵn thư mục `.github/workflows`).
2. Vào tab **Actions**, workflow **Build APK** sẽ tự chạy khoảng 3 đến 5 phút.
3. Mở lượt chạy vừa xong, tải **AutoTap-apk** ở mục *Artifacts*, giải nén ra là có file APK.

### Cách C: dòng lệnh
Máy cần có JDK 17 và Android SDK, đồng thời đặt biến `ANDROID_HOME`. Sau đó chạy:
```
./gradlew assembleDebug
```

## 2. Cài đặt và cấp quyền
1. Chép APK vào điện thoại và cài đặt. Nếu máy hỏi, hãy cho phép "cài ứng dụng không rõ nguồn gốc".
2. Mở AutoTap, bấm **Mở cài đặt Trợ năng**, tìm **AutoTap** rồi bật lên.
3. **Android 13 trở lên** có thể báo *"Cài đặt bị hạn chế"* vì app được cài ngoài CH Play. Khi đó:
   vào **Cài đặt → Ứng dụng → AutoTap**, bấm **⋮** ở góc trên và chọn **Cho phép chế độ cài đặt bị hạn chế**. Sau đó quay lại bật Trợ năng.
   (Trong app có nút *"Không bật được? Mở thông tin ứng dụng"* để vào thẳng trang này.)
4. Máy Xiaomi, Oppo, Vivo và Samsung thường có chế độ tiết kiệm pin. Hãy đặt AutoTap ở chế độ **Không hạn chế pin** để dịch vụ không bị tắt ngầm.

## 3. Cách dùng

### Nút combo
Mỗi combo đang bật có **một nút tròn riêng**, đánh số theo thứ tự trong danh sách.

| Thao tác | Tác dụng |
|---|---|
| Chạm | Chạy hoặc dừng combo. Chạm nút của combo khác khi đang chạy thì chuyển sang combo đó. |
| Kéo | Di chuyển nút đến vị trí khác (vị trí được nhớ riêng cho từng combo) |
| Giữ lâu | Mở nhanh phần chỉnh combo: tốc độ, vòng lặp, phím vật lý… |

Màu nút cho biết trạng thái: xanh dương là đang chờ, cam là đang chạy. Độ hiển thị chỉnh được từ **0% (tàng hình)** đến 100%. Khi đang mở bảng điều khiển, nút tàng hình vẫn hiện mờ để bạn thấy mà kéo.

### Danh sách combo (vuốt từ cạnh màn hình)
Vuốt từ **cạnh phải** màn hình vào trong (chỗ có vạch mờ nhỏ; đổi được sang cạnh trái). Bảng danh sách combo sẽ mở ra, dùng được ngay trong trò chơi:
* Công tắc **Bảng điều khiển** và **Nhận diện hình ảnh**.
* Danh sách combo: bật hoặc tắt công tắc để hiện hoặc ẩn nút combo. Chạm tên combo để chỉnh nhanh.
* Nút **Ghi combo** và **Auto click** để tạo combo mới.
* **Tùy chỉnh**: kích thước và độ hiển thị nút combo, kích thước bảng điều khiển, cạnh vuốt.

### Bảng điều khiển (thanh công cụ)
Ẩn bảng điều khiển **không** làm mất nút combo.

| Nút | Chức năng |
|---|---|
| ≡ (trên cùng) | Kéo để di chuyển bảng |
| ● | **Ghi combo mới**. Bấm ■ để dừng và lưu |
| ＋ / − | Thêm hoặc bớt **điểm auto click**. Kéo dấu tròn đến đúng vị trí; giữ lâu dấu tròn để xoá |
| ✂ | **Chụp màn hình và khoanh vùng hình mẫu** cho tính năng nhận diện |
| 👁 | Bật hoặc tắt **nhận diện hình ảnh** |
| ☰ | Mở danh sách combo |
| ⌂ | Mở app |
| ✕ | Ẩn bảng điều khiển |

### Gán phím vật lý
1. Chạm vào tên một combo (trong danh sách combo hoặc trong app), hoặc giữ lâu nút combo.
2. Bấm **Gán phím**, rồi nhấn phím muốn dùng, ví dụ tăng hoặc giảm âm lượng, hay phím tắt riêng nếu máy có.
3. Chọn kiểu bấm **Nhấn 1 lần / Nhấn đúp / Giữ lâu**, rồi bấm **Lưu**.
* Một phím có thể điều khiển tối đa 3 combo, mỗi combo một kiểu bấm.
* Với phím âm lượng, kiểu bấm nào chưa gán combo thì vẫn chỉnh âm lượng như bình thường.
* Phím vẫn hoạt động khi đã tắt giao diện nổi. Phím không hoạt động khi màn hình tắt.
* Một số phím như nút nguồn hoặc phím trợ lý của vài hãng bị hệ thống giữ riêng, ứng dụng không bắt được.

### Ghi combo và phát lại nhanh hơn
1. Bấm ● rồi thao tác như bình thường. Mỗi lần chạm, giữ hoặc vuốt được ghi lại và **chuyển tiếp xuống ứng dụng bên dưới**, nên bạn vẫn thấy kết quả ngay.
   Hãy thao tác **từ tốn**, chờ khoảng 0,1 giây giữa các lần chạm để lần chuyển tiếp trước kịp hoàn tất.
2. Bấm ■ (trên thanh hoặc trên nút kích hoạt) để lưu. Thao tác vừa ghi sẽ tự được gán cho nút kích hoạt.
3. Vào app, mục **3. Thao tác**, chạm vào thao tác vừa ghi để chỉnh:
   * **Tốc độ** từ 0,25x đến 10x. Tốc độ này rút ngắn cả thời gian chờ giữa các bước lẫn thời gian mỗi cú vuốt. Thao tác *giữ lâu* (long‑press) vẫn giữ nguyên thời gian để không bị đổi thành chạm thường.
   * **Số vòng lặp**, trong đó 0 là lặp vô hạn cho tới khi bạn bấm dừng.
   * **Thời gian nghỉ giữa các vòng**.
4. Bấm nút kích hoạt để chạy.

### Nhận diện hình ảnh
1. Mở màn hình có hình cần nhận diện (ví dụ nút "Nhận thưởng"), rồi bấm ✂ trên thanh công cụ.
2. Kéo ngón tay khoanh vùng quanh hình và bấm **Lưu vùng này**.
3. Chọn việc cần làm khi thấy hình: **click vào giữa hình**, hoặc **chạy một thao tác đã ghi**.
4. Việc theo dõi bắt đầu ngay. Từ đó, mỗi khi hình xuất hiện lại **ở vị trí đó** (cho phép lệch ± 40 px), thao tác sẽ tự chạy.
5. Trong app, mục **4**, bạn chỉnh được:
   * **Độ giống tối thiểu**, mặc định 85%. Giá trị cao thì ít nhận nhầm hơn, giá trị thấp thì dễ nhận ra hình hơn.
   * **Sai lệch vị trí cho phép** (px).
   * **Thời gian chờ trước khi được kích hoạt lại**, để tránh bấm liên tục vào cùng một hình.
   * Bật hoặc tắt từng hình, và **chu kỳ quét** màn hình.

## 4. Lưu ý và giới hạn
* Hình mẫu gắn với **hướng màn hình lúc chụp**. Nếu bạn xoay màn hình, hình đó tạm thời không được so khớp.
* Khi đang ghi, app chỉ ghi **một ngón tay**, không ghi thao tác nhiều ngón như chụm để phóng to.
* Trên Android 11, hệ thống chỉ cho chụp màn hình khoảng 1 lần mỗi giây, nên chu kỳ quét tối thiểu là 1000 ms. Từ Android 12 trở lên là khoảng 350 ms.
* Một số ứng dụng như ngân hàng hay game có chống gian lận có thể chặn chụp màn hình hoặc thao tác tự động.
* Chỉ dùng app cho những ứng dụng và trò chơi cho phép tự động hoá. Nhiều game coi auto click là vi phạm điều khoản.

## 5. Cấu trúc mã nguồn
```
app/src/main/java/com/autotap/app/
├── AutoClickService.kt   Dịch vụ Trợ năng: gửi thao tác, chụp màn hình, quản lý giao diện nổi
├── FloatingUi.kt         Nút kích hoạt, thanh công cụ, ghi thao tác, điểm click, khoanh vùng hình
├── GesturePlayer.kt      Phát lại thao tác theo tốc độ và số vòng lặp
├── ImageMatcher.kt       So khớp hình mẫu (tương quan chéo chuẩn hoá trên ảnh màu thu nhỏ)
├── ImageWatcher.kt       Quét màn hình định kỳ và kích hoạt thao tác khi thấy hình
├── OverlayViews.kt       Tiện ích cửa sổ nổi, kéo thả, lớp ghi thao tác, lớp chọn vùng
├── MainActivity.kt       Màn hình cài đặt
├── Form.kt               Trình dựng hộp thoại
├── Models.kt / Store.kt  Mô hình dữ liệu và lưu trữ JSON
```
