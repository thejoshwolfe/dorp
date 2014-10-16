
; defined in the main .dorp file
declare void @entry_point()
define i32 @main() {
  call void @entry_point()
  ret i32 0;
}

@percent_d_format = private unnamed_addr constant [3 x i8] c"%d\00", align 1
define void @dorp.print(i32 %value) nounwind uwtable {
  ; char buffer[10];
  %buffer = alloca [10 x i8], align 1
  ; char * buffer_ptr = buffer;
  %buffer_ptr = getelementptr inbounds [10 x i8]* %buffer, i64 0, i64 0
  ; sprintf(buffer_ptr, "%d", value)
  call i32 (i8*, i8*, ...)* @sprintf(i8* %buffer_ptr, i8* getelementptr inbounds ([3 x i8]* @percent_d_format, i64 0, i64 0), i32 %value) nounwind
  ; size_t len = strlen(buffer_ptr);
  %len = call i64 @strlen(i8* %buffer_ptr) nounwind readonly
  ; write(1, buffer_ptr, len)
  call i64 @write(i32 1, i8* %buffer_ptr, i64 %len) nounwind
  ret void
}


; #include <stdio.h>
declare i32 @sprintf(i8* nocapture, i8* nocapture readonly, ...) nounwind
; #include <unistd.h>
declare i64 @write(i32, i8* nocapture readonly, i64)
; #include <string.h>
declare i64 @strlen(i8* nocapture) nounwind readonly
