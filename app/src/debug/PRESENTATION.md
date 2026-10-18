# Thuyết trình từ màn demo Media3 + PiP

Code chính: `java/com/example/pgck2/demo/MediaDemoActivity.java`. Đây là Java + XML, chưa dùng Compose. Các đoạn dưới là trích rút gọn để giải thích, không phải những hàm hoàn chỉnh để chép chạy riêng.

## Cách chia bài 15–20 phút

Giới thiệu và so sánh VideoView khoảng 2 phút. Mức 1 khoảng 2 phút. Mức 2 khoảng 3 phút. Mức 3 về chất lượng, CC và dub khoảng 4 phút. PiP khoảng 4 phút. Chèn demo và kết luận khoảng 2 phút. Nếu clip dài, rút phần giới thiệu để giữ tổng thời gian.

## Mở đầu

“Nhóm chia màn demo thành ba mức để phân biệt phần có sẵn của thư viện và phần tự tích hợp. Mức 1 chỉ phát nội dung. Mức 2 nối các nút điều khiển với API của player. Mức 3 quản lý chất lượng, các nguồn chữ/tiếng và PiP.”

“VideoView phù hợp với nhu cầu phát cơ bản. Media3 có các lớp chuyên dụng cho chọn track, ghép nguồn và cấu hình buffer, nên dễ mở rộng trình phát trong bài này. VideoView vẫn có hỗ trợ phụ đề và có thể kết hợp PiP, vì vậy nhóm không xem những chức năng đó là độc quyền của ExoPlayer.”

## Mức 1: player, nguồn và giao diện

Chỉ vào `startPlayer()` và `loadSource()`:

```java
player = new ExoPlayer.Builder(this).build();
playerView.setPlayer(player);
player.setMediaSource(source);
player.prepare();
player.setPlayWhenReady(true);
```

“ExoPlayer xử lý phát. PlayerView hiển thị hình và thanh điều khiển. Source mô tả nguồn cần tải. prepare bắt đầu chuẩn bị nguồn và dữ liệu. setPlayWhenReady bằng true yêu cầu player phát khi đã đủ điều kiện.”

“Nhóm dùng chung giao diện player có sẵn của project và ẩn những nút chưa thuộc mức hiện tại. Vì vậy mức 1 thể hiện chức năng cơ bản, không có nghĩa Media3 mặc định tự vẽ đúng giao diện tùy biến này.”

Quay clip: chọn 1, phát/tạm dừng và kéo timeline.

## Mức 2: buffer, tua, tốc độ và volume

Chỉ vào cấu hình trong `startPlayer()`:

```java
.setBufferDurationsMs(15000, 30000, 1500, 3000)
.setSeekBackIncrementMs(10000)
.setSeekForwardIncrementMs(10000)
```

“Buffer là dữ liệu hình và tiếng đã tải nhưng chưa phát đến. Nếu đang xem giây 40 mà đã tải đến giây 60 thì còn khoảng 20 giây dữ liệu phía trước. Khi phần này hết mà mạng chưa tải kịp, player dừng chờ.”

“Hai số đầu là ngưỡng điều khiển buffer 15–30 giây nội dung. 1500 là khoảng 1,5 giây nội dung để bắt đầu phát. 3000 là khoảng 3 giây trước khi phát tiếp sau khi đã hết buffer. Đây là thời lượng nội dung tích lũy, không phải luôn ngồi chờ đúng 1,5 hay 3 giây. Các ngưỡng còn phụ thuộc điều kiện phát. Tăng buffer có thể giảm dừng chờ nhưng tốn thêm bộ nhớ và dữ liệu.”

Chỉ vào `showSpeed()` và `bindController()`:

```java
player.setPlaybackSpeed(2f);
float volume = progress / 100f;
player.setVolume(volume);
```

“2x là hệ số tốc độ: 20 giây nội dung mất khoảng 10 giây thực tế nếu không bị gián đoạn. Còn tua 10 giây là nhảy vị trí và bỏ qua đoạn đó. Thanh volume đi từ 0 đến 100 nên nhóm chia cho 100f để chuyển về khoảng 0 đến 1. Đây là gain của player, không phải EQ hay làm âm thanh trong hơn.”

Quay clip: chọn 2, tua một lần, chọn 0.5x rồi 2x, kéo volume và mute/unmute.

## Mức 3: HD và Auto

Chỉ vào `showQuality()` và `applyTrackChoices()`:

```java
int height = group.getTrackFormat(i).height;
new TrackSelectionOverride(group.getMediaTrackGroup(), i);
```

“Menu lấy các video track thực sự có trong nguồn và được thiết bị hỗ trợ. height là chiều cao hình, i là vị trí track. Chọn 750p nghĩa là tìm track tương ứng rồi đặt override, không truyền số 750 làm vị trí track.”

“Nguồn mẫu này có 100p, 200p, 350p và 750p. Auto xóa lựa chọn cố định để Media3 tự chọn giữa những track có thể thích ứng, theo mạng và buffer. Dữ liệu cũ còn trong buffer nên hình có thể chưa đổi ngay. Một MP4 chỉ có một video track thì không tự xuất hiện nhiều mức HD.”

Quay clip: chọn 3, mở HD, chọn mức thấp rồi mức cao và quay về Auto. Đọc con số trên badge thay vì khẳng định nhãn menu là độ phân giải đang xuất ngay lập tức.

## CC và dub rời

Chỉ vào `loadSource()`:

```java
new MediaItem.SubtitleConfiguration.Builder(subUri)
    .setMimeType(MimeTypes.TEXT_VTT)
    .setLanguage("vi")
    .setLabel("Demo 1")
    .build();
```

“URL trỏ tới file chữ có timestamp. MIME khai báo định dạng, vi là mã ngôn ngữ, label là tên trong menu. Nhóm gắn các config vào MediaItem. Khi chọn CC, nhóm đặt override đúng text track để hai bản cùng tiếng Việt vẫn chọn riêng được. Với SRT phải dùng MIME SubRip tương ứng.”

```java
MediaSource merged =
    new MergingMediaSource(true, false, picture, audio);
```

“picture giữ VIDEO và TEXT của nguồn gốc, bỏ AUDIO gốc. audio chỉ giữ AUDIO của file dub. Sau đó MergingMediaSource ghép hai nguồn để phát cùng nhau. Không cần xuất file video mới. Nhóm lưu vị trí và ý định phát trước khi thay source, rồi seek về vị trí cũ.”

“Dub và CC minh họa hiện tại do nhóm nhập URL thủ công. Nếu khác nội dung hoặc thời lượng thì không tự đồng bộ lời thoại. Ghép nguồn không có nghĩa player tự dịch hoặc tự căn tiếng.”

Quay clip: chọn Demo 1/Demo 2/Tắt, đổi Dub demo 1/Dub demo 2/Âm thanh gốc. Nên quay ở đoạn mà file âm thanh và phụ đề còn dữ liệu.

## PiP: vào chế độ và chọn ngang/dọc

Chỉ vào manifest demo, `showPipOptions()`, `pipParams()` và `enterPip()`:

```java
// Thuộc tính của Activity trong AndroidManifest.xml
android:supportsPictureInPicture="true"

// Java, chạy sau khi kiểm tra API/feature
new PictureInPictureParams.Builder()
    .setAspectRatio(new Rational(16, 9))
    .setSourceRectHint(bounds)
    .build();
```

“PiP là chế độ cửa sổ của Android. App khai báo hỗ trợ rồi đưa Activity hiện tại vào PiP. Nhóm dùng lại player đang chạy nên không khởi tạo video mới.”

“16:9 tạo khung ngang, 9:16 tạo khung dọc. Theo tỷ lệ video thì tính từ chiều rộng, chiều cao và tỷ lệ pixel của nguồn. sourceRectHint chỉ vùng video để hệ thống làm chuyển cảnh. Nó không đặt kích thước cửa sổ.”

“Android quản lý kích thước PiP. Trên Android 12 trở lên, chạm hai lần đổi cỡ nhỏ/lớn và pinch bằng hai ngón để co/giãn. Nhóm có thể chỉnh gần cỡ mong muốn, ví dụ khoảng 25%, nhưng API không bảo đảm đúng 25% diện tích màn hình. Đổi khung dọc không biến nội dung ngang thành video dọc, nên có thể có viền đen.”

Quay clip: chọn PiP ngang, chạm một lần hiện nút, chạm hai lần đổi cỡ, kéo cửa sổ sang góc khác. Với emulator có thể dùng thao tác pinch nếu phiên bản emulator hỗ trợ mô phỏng hai ngón. Sau đó khôi phục Activity, chọn PiP dọc để so sánh. Không cần bật quyền vẽ đè.

## Ba nút khi đang ở PiP

Chỉ vào `pipAction()` và receiver:

```java
Intent intent = new Intent(ACTION)
    .setPackage(getPackageName())
    .putExtra(EXTRA_COMMAND, command);
PendingIntent pending = PendingIntent.getBroadcast(
    this, 9400 + command, intent,
    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
```

“Mỗi nút là một RemoteAction. Android nhận PendingIntent để biết phải gửi lệnh nào khi người dùng bấm. Ba request code khác nhau giữ ba PendingIntent riêng biệt, nếu dùng chung thì các nút có thể gửi nhầm cùng một lệnh.”

```java
case COMMAND_REWIND: player.seekBack(); break;
case COMMAND_PLAY_PAUSE:
    if (player.getPlayWhenReady()) player.pause(); else player.play();
    break;
case COMMAND_FORWARD: player.seekForward(); break;
```

“Receiver nhận command rồi gọi API player. Hai lệnh seek dùng bước 10000 ms đã cấu hình, nên mỗi lần nhảy 10 giây. Seek không tự đổi trạng thái pause sang play. Play/Pause kiểm tra ý định phát bằng getPlayWhenReady để khi đang buffering vẫn tạm dừng được. Sau thao tác, nhóm cập nhật params để icon đổi theo trạng thái.”

“Android giới hạn số action hiển thị. Code ưu tiên đủ ba nút khi thiết bị cho phép, nếu ít chỗ hơn thì giữ Play/Pause. Seek bị vô hiệu với nguồn không cho tua.”

Quay clip: vào PiP, chạm một lần hiện action, pause, tiến/lùi 10 giây, play lại, khôi phục và kiểm tra timeline.

## Vòng đời

“Khi vào PiP, nhóm ẩn header, thông tin và controller của màn đầy đủ. Android vẽ bộ nút PiP riêng. Không release player chỉ vì onPause. Khi Activity không còn hiển thị và đi vào onStop, nhóm lưu vị trí, ý định phát, tốc độ, volume rồi giải phóng. onStart dựng lại để tiếp tục.”

“Biến Activity không phải lịch sử xem bền vững sau khi tiến trình bị hủy. Phát nền bằng service là một phần mở rộng khác, màn demo chưa làm.”

## Nguồn đối chiếu

- https://developer.android.com/media/media3/exoplayer
- https://developer.android.com/media/media3/exoplayer/track-selection
- https://developer.android.com/reference/androidx/media3/exoplayer/DefaultLoadControl.Builder
- https://developer.android.com/reference/androidx/media3/exoplayer/source/MergingMediaSource
- https://developer.android.com/develop/ui/views/picture-in-picture
- https://developer.android.com/reference/android/app/PictureInPictureParams.Builder
